# -*- coding: utf-8 -*-
"""
§11.2 新增界面 · 浏览器实测取证（Playwright + 自带 Chromium）。

**为什么不用无头 Edge**：本机 msedge 常驻，`--headless=new --dump-dom` 与截图都不可信
（stdout 0 字节 / 被常驻实例接管）。Playwright 自带的 chromium-1223 是独立实例，
完全不受影响；且它能精确控制视口宽度、真实滚动、正常推进 CSS transition。

**做什么**：对《系统功能使用说明》§11.2「尚未上线的界面」新增的 13 个页面，
在 3 个视口下逐页真实渲染、逐页截图，并逐页采集实测指标
（横向溢出 / 控制台错误 / 4xx-5xx 请求 / DOM 形态断言），产出
① 截图 PNG ② 指标 JSON ③ 可直接粘进报告的 Markdown 表格。

**登录怎么过**：底座登录有图形验证码。这里不 OCR 图片，而是走 Redis——
后端把验证码存成 `redis key = md5(checkKey + signatureSecret) + lowerCase(code)`，
value 就是验证码本身（见 LoginController#randomImage）。拿到 checkKey 就能算出
key 前缀，扫前缀取 value，验证码即解出。免去人工/OCR，且不修改任何生产配置。

用法：
    python 2026103024-浏览器取证.py [--out <截图目录>] [--json <指标json>]

退出码：0 = 全部页面取证完成；1 = 有页面没截到（详见输出）。
"""
import argparse
import hashlib
import json
import os
import re
import sys
import time

from playwright.sync_api import sync_playwright

# ------------------------------------------------------------------ 常量

# 前端地址：默认打**开发服务器**（vite dev）。
# 打**发布候选产物**时用 --front 覆盖（例：`--front http://127.0.0.1:4173`，
# 4173 是 `vite preview` 托管 builds/latest/frontend/dist 的端口）。
# 这个开关是必需的：2026-11-05 挖到的 E-51（上一版发布候选的前端产物少 1389 个按需加载 chunk）
# 只有在**真实构建产物**上跑一遍才能暴露 —— 对着 dev server 取证永远看不到产物残缺。
FRONT = os.environ.get("STUDY_FRONT", "http://127.0.0.1:8589")
BACKEND = "http://127.0.0.1:8590/jeecg-boot"
# dev profile 的 jeecg.signatureSecret（application-dev.yml），验证码 redis key 前缀要用
SIGNATURE_SECRET = "dd05f1c54d63749eda95f9fa6d49v442a"
REDIS_HOST, REDIS_PORT, REDIS_DB = "127.0.0.1", 6379, 0

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_SHOTS = os.path.join(REPO, "03-测试验证", "01-测试文档", "02-取证截图")
DEFAULT_JSON = os.path.join(REPO, "03-测试验证", "01-测试文档", "02-取证数据.json")

VIEWPORTS = [
    {"key": "D", "w": 1440, "h": 900, "label": "桌面 1440×900"},
    {"key": "T", "w": 768, "h": 1024, "label": "平板 768×1024"},
    {"key": "M", "w": 390, "h": 844, "label": "手机 390×844"},
]

# 33 个页面态。
#   A01-A09 / B01-B03 / C01-C02 = §11.2 的既有 13 个（管理端 9 + 家长端 3 + 孩子端 2）
#   D01-D18 / E01-E02            = 2026-11-04 §3.15.4 上线的 20 个页面（学习端 18 + 家长端 2）
# code 用于文件名与报告里的编号；role 决定用哪个身份登录。
# 带 :code / :unitCode 的路径用的是**库里的真实取值**（不是占位符）：
#   ERR-000491（属 demo 孩子 ST-CHILD-DEMO-01）· MTH-MA-000001 · GRP-0007 · U01
PAGES = [
    ("A01", "管理端", "上下文配置", "/study/manage/context", "admin"),
    ("A02", "管理端", "坐标树", "/study/manage/coordinate", "admin"),
    ("A03", "管理端", "考点维护", "/study/manage/coordinate/KPT-MA-000001", "admin"),
    ("A04", "管理端", "考点版本历史", "/study/manage/coordinate/KPT-MA-000001/history", "admin"),
    ("A05", "管理端", "题目维护", "/study/manage/question", "admin"),
    ("A06", "管理端", "母题维护", "/study/manage/mother", "admin"),
    ("A07", "管理端", "词表维护", "/study/manage/word", "admin"),
    ("A08", "管理端", "语法点维护", "/study/manage/grammar", "admin"),
    ("A09", "管理端", "拆解库维护", "/study/manage/writing", "admin"),
    ("B01", "家长端", "家庭成员", "/study-parent/family", "parent"),
    ("B02", "家长端", "孩子档案", "/study-parent/child", "parent"),
    ("B03", "家长端", "关联管理", "/study-parent/child/CHD-000001/relations", "parent"),
    ("C01", "孩子端", "孩子登录页·家庭码", "/study/kid/login", "kid"),
    ("C02", "孩子端", "孩子登录页·选孩子与PIN", "/study/kid/login", "kid"),
    # ↓ 2026-11-05 补齐：上一轮上线的 20 个页面此前零浏览器取证
    ("D01", "孩子端", "今日首页", "/study/kid/home", "kid"),
    ("D02", "孩子端", "今日复习", "/study/kid/review", "kid"),
    ("D03", "孩子端", "错题录入", "/study/kid/error/import", "kid"),
    ("D04", "孩子端", "我的错题本", "/study/kid/error", "kid"),
    ("D05", "孩子端", "错题详情", "/study/kid/error/ERR-000491", "kid"),
    ("D06", "孩子端", "错题统计", "/study/kid/error/statistics", "kid"),
    ("D07", "孩子端", "课内单元闯关", "/study/kid/word/unit/U01", "kid"),
    ("D08", "孩子端", "课外词线", "/study/kid/word/out", "kid"),
    ("D09", "孩子端", "熟词测试", "/study/kid/word/known-test", "kid"),
    ("D10", "孩子端", "背诵统计", "/study/kid/word/stats", "kid"),
    ("D11", "孩子端", "数学闯关", "/study/kid/math", "kid"),
    ("D12", "孩子端", "母题关卡", "/study/kid/math/mother/MTH-MA-000001", "kid"),
    ("D13", "孩子端", "母题讲解", "/study/kid/math/mother/MTH-MA-000001/teach", "kid"),
    ("D14", "孩子端", "母题变式练习", "/study/kid/math/variant/MTH-MA-000001", "kid"),
    ("D15", "孩子端", "口述确认", "/study/kid/math/oral/MTH-MA-000001", "kid"),
    ("D16", "孩子端", "语法点详情", "/study/kid/grammar/GRP-0007", "kid"),
    ("D17", "孩子端", "句子变形器", "/study/kid/grammar/transform", "kid"),
    ("D18", "孩子端", "错句收藏馆", "/study/kid/grammar/collection", "kid"),
    ("E01", "家长端", "周报", "/study-parent/weekly", "parent"),
    ("E02", "家长端", "薄弱考点", "/study-parent/weakpoint", "parent"),
]

# 账号（底座演示账号，明文口令只在本机测试库使用，不入库）
ACCOUNTS = {
    "admin": ("admin", "123456"),
    "parent": ("study_parent_demo", "study@2026"),
}
KID_FAMILY_CODE = "STU026"
KID_PIN = "1234"

# R-04 禁止出现在界面上的「公开比较」措辞
R04_WORDS = ["排名", "排行", "百分位", "名次", "排行榜", "超过", "位次"]

# 页面上出现的**业务错误提示**（D-4 的验证点：数据加载成功后仍挂着错误提示）
ERR_PHRASES = [
    "请指定要访问的孩子",
    "无权访问",
    "系统异常",
    "请求失败",
    "加载失败",
]

# 页面里采指标的 JS
METRICS_JS = r"""
() => {
  const de = document.documentElement;
  const q = (s) => document.querySelectorAll(s).length;
  const txt = (document.body.innerText || '').replace(/\s+/g, ' ').trim();

  // 操作列形态：表格单元格里到底是链接还是按钮
  let tdLink = 0, tdBtn = 0, actionSamples = [];
  document.querySelectorAll('table tbody td').forEach(td => {
    tdLink += td.querySelectorAll('a').length;
    tdBtn  += td.querySelectorAll('button').length;
  });
  document.querySelectorAll('table tbody tr').forEach(tr => {
    const cells = [...tr.children];
    const last = cells[cells.length - 1];
    if (last) {
      const s = (last.innerText || '').replace(/\s+/g, ' ').trim();
      if (s && actionSamples.length < 6) actionSamples.push(s.slice(0, 40));
    }
  });

  // **数据列是否真的有内容**（D-3 的验证点：整列空白时 td 全是空串）
  // 注意不能只看「操作列有链接」——操作列走的是 slot，与 dataIndex 无关。
  let tdTotal = 0, tdEmpty = 0, dataCellSamples = [];
  document.querySelectorAll('table tbody tr').forEach((tr, ri) => {
    const cells = [...tr.children];
    cells.forEach((td, ci) => {
      tdTotal++;
      const s = (td.innerText || '').replace(/\s+/g, ' ').trim();
      if (!s) tdEmpty++;
    });
    // 每行去掉最后一列（操作列），剩下的是数据列
    const dataCells = cells.slice(0, Math.max(cells.length - 1, 0))
      .map(td => (td.innerText || '').replace(/\s+/g, ' ').trim());
    if (ri < 4) dataCellSamples.push(dataCells.join(' | ').slice(0, 120));
  });

  // 表单布局（antd：horizontal / vertical）
  const forms = [...document.querySelectorAll('form')].map(f => {
    if (f.classList.contains('ant-form-horizontal')) return 'horizontal';
    if (f.classList.contains('ant-form-vertical')) return 'vertical';
    return 'none';
  });

  // 首屏标题
  let heading = '';
  const h = document.querySelector('main h1, main h2, main h3, .ant-page-header-heading-title, .ant-card-head-title');
  if (h) heading = (h.innerText || '').replace(/\s+/g, ' ').trim().slice(0, 40);

  const bad = %s.filter(w => txt.includes(w));
  const errHits = %s.filter(w => txt.includes(w));

  return {
    url: location.pathname + location.search,
    title: document.title,
    heading: heading,
    scrollW: de.scrollWidth,
    clientW: de.clientWidth,
    overflow: de.scrollWidth - de.clientWidth,
    docH: de.scrollHeight,
    textLen: txt.length,
    primaryBtn: q('button.ant-btn-primary, a.ant-btn-primary'),
    linkBtn: q('a.ant-btn'),
    tables: q('table'),
    theads: q('table thead'),
    rows: q('table tbody tr'),
    tdTotal: tdTotal,
    tdEmpty: tdEmpty,
    dataCellSamples: dataCellSamples,
    expandIcon: q('.ant-table-row-expand-icon, .ant-table-expand-icon'),
    tdLink: tdLink,
    tdBtn: tdBtn,
    actionSamples: actionSamples,
    forms: forms,
    formHorizontal: forms.filter(f => f === 'horizontal').length,
    formVertical: forms.filter(f => f === 'vertical').length,
    tableSetting: q('.ant-table-setting'),
    emptyState: q('.ant-empty, .ant-empty-normal, .ant-empty-image'),
    loadingMask: q('.ant-spin-spinning'),
    modal: q('.ant-modal-wrap'),
    drawer: q('.ant-drawer-open'),
    r04Hits: bad,
    errHits: errHits,
    text: txt.slice(0, 900),
  };
}
"""


# ------------------------------------------------------------------ 认证

# 底座把**所有**缓存塞进一个复合 localStorage 键
# （JEECGBOOT_PRO__DEVELOPMENT__3.9.2__COMMON__LOCAL__KEY__ → {TOKEN__:{value:...}}），
# 所以不能只看「有没有叫 TOKEN__ 的顶层键」——必须解开这层复合结构。
TOKEN_JS = """() => {
  for (const k of Object.keys(localStorage)) {
    try {
      const outer = JSON.parse(localStorage.getItem(k) || 'null');
      const inner = outer && outer.value;
      const t = inner && inner.TOKEN__ && inner.TOKEN__.value;
      if (typeof t === 'string' && t.length > 20) return t;
    } catch (e) { /* 不是 JSON，继续找 */ }
  }
  for (const k of Object.keys(sessionStorage)) {
    try {
      const outer = JSON.parse(sessionStorage.getItem(k) || 'null');
      const inner = outer && outer.value;
      const t = inner && inner.TOKEN__ && inner.TOKEN__.value;
      if (typeof t === 'string' && t.length > 20) return t;
    } catch (e) { /* 同上 */ }
  }
  return '';
}"""


def wait_token(page, timeout_ms=60000):
    """等 token 真正落进底座的复合缓存里。

    超时给到 60 秒：vite dev server **冷启动**时首次编译登录页可能要几十秒，
    登录请求本身会卡在编译等待上 —— 30 秒不够，会把**成功的登录**判成失败
    （2026-11-05 实测：同一个脚本 admin 首次失败、稍后重跑即成功）。
    """
    deadline = time.time() + timeout_ms / 1000.0
    while time.time() < deadline:
        try:
            if page.evaluate(TOKEN_JS):
                return True
        except Exception:
            pass
        page.wait_for_timeout(200)
    return False


def fetch_captcha(check_key):
    """自己向底座要一张验证码（后端把 code 存进 redis），并从 redis 解出来。

    为什么自己拿、而不是读页面上那张：
      登录页在挂载与失败重试时都会刷新验证码图，**图上显示的那张**与
      「最后一次 /sys/randomImage 请求」可能不是同一张（响应有先后），
      于是「按最后一个请求解出来的码」和「界面上那张图」对不上 → 验证码错误。
      自己造一个 checkKey 并在**登录请求里替换**成这一对，就彻底没有竞态。
    """
    import urllib.request
    url = "%s/sys/randomImage/%s" % (BACKEND, check_key)
    with urllib.request.urlopen(url, timeout=20) as r:
        r.read()
    prefix = hashlib.md5((check_key + SIGNATURE_SECRET).encode("utf-8")).hexdigest()
    import redis
    r = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, db=REDIS_DB, decode_responses=True)
    keys = list(r.scan_iter(match=prefix + "*", count=100))
    if not keys:
        raise RuntimeError("redis 未找到验证码 key（checkKey=%s）" % check_key)
    from_value = (r.get(keys[0]) or "").strip('"')
    from_key = keys[0][len(prefix):]
    if from_value.lower() != from_key.lower():
        raise RuntimeError("验证码两条来源不一致 value=%r key=%r" % (from_value, from_key))
    return from_value


def login_base(page, account, password):
    """走**底座真实登录表单**：填账号/密码/验证码 → 点「登 录」→ 等 token 落库。

    做法：拦下 /sys/login 的请求体，把 captcha / checkKey 换成自己那一对，
    后端校验的就是「自己造的 redis 键」，与页面上显示哪张验证码图无关。

    注意按钮文案：antd 的 ConfigProvider 会给**两个汉字之间自动插空格**
    （「登 录」），用文案匹配要写成 '登 录'，这里直接用稳定类名。
    """
    check_key = "jc%s%d" % (account[:4], int(time.time() * 1000) % 1000000)
    code = fetch_captcha(check_key)

    def rewrite(route):
        req = route.request
        body = req.post_data_json
        if isinstance(body, dict):
            body["captcha"] = code
            body["checkKey"] = check_key
        headers = {k: v for k, v in req.headers.items()
                   if k.lower() not in ("content-length", "host")}
        headers["content-type"] = "application/json"
        route.continue_(post_data=json.dumps(body, ensure_ascii=False), headers=headers)

    page.route(re.compile(r"/sys/login$"), rewrite)

    page.goto(FRONT + "/login", wait_until="domcontentloaded", timeout=60000)
    page.wait_for_selector("input[placeholder='验证码']", timeout=30000)
    page.fill("input[placeholder='账号']", account)
    page.fill("input[placeholder='密码']", password)
    page.fill("input[placeholder='验证码']", code)
    page.click("button.aui-link-login")

    if not wait_token(page):
        raise RuntimeError("登录超时：%s 未拿到 token" % account)
    page.unroute(re.compile(r"/sys/login$"))
    return True


def kid_step1(page):
    """孩子端步骤①：填家庭码 → 点底部主按钮 → 进入「选孩子 + PIN」。"""
    page.goto(FRONT + "/study/kid/login", wait_until="domcontentloaded", timeout=60000)
    page.wait_for_selector("input[placeholder='6 位家庭码']", timeout=30000)
    page.fill("input[placeholder='6 位家庭码']", KID_FAMILY_CODE)
    page.wait_for_timeout(600)
    page.click(".study-kid-login__primary")
    page.wait_for_selector("input[placeholder*='PIN']", timeout=30000)
    page.wait_for_timeout(600)
    return True


def login_kid(page):
    """走**孩子端真实两步登录**：家庭码 → 选孩子 → PIN。"""
    kid_step1(page)
    page.fill("input[placeholder*='PIN']", KID_PIN)
    page.wait_for_timeout(600)
    page.click(".study-kid-login__primary")
    if not wait_token(page):
        raise RuntimeError("孩子端登录超时：未拿到 token")
    return True


def make_state(browser, role, tmpdir):
    """登录一次，把 storage_state 存下来给各视口复用（省掉重复登录）。"""
    path = os.path.join(tmpdir, "state-%s.json" % role)
    ctx = browser.new_context(viewport={"width": 1440, "height": 900})
    page = ctx.new_page()
    if role == "kid":
        login_kid(page)
    else:
        account, password = ACCOUNTS[role]
        login_base(page, account, password)
    ctx.storage_state(path=path)
    ctx.close()
    return path


# ------------------------------------------------------------------ 取证

def capture(page, url, shot_path, prepare=None):
    """打开页面 → 等稳定 → 采指标 → 截图。返回 (指标, 控制台错误, 失败请求)。"""
    console_errors = []
    bad_responses = []

    def on_console(msg):
        if msg.type == "error":
            console_errors.append(msg.text[:220])

    def on_response(resp):
        if resp.status >= 400:
            bad_responses.append({"status": resp.status, "url": resp.url[:160]})

    page.on("console", on_console)
    page.on("response", on_response)

    err = None
    try:
        if prepare:
            # prepare 自己负责导航与状态准备（例如把孩子端推到「步骤②」）
            prepare(page)
        else:
            page.goto(url, wait_until="domcontentloaded", timeout=60000)
        try:
            page.wait_for_load_state("networkidle", timeout=12000)
        except Exception:
            pass
        page.wait_for_timeout(2200)
        metrics = page.evaluate(METRICS_JS % (
            json.dumps(R04_WORDS, ensure_ascii=False),
            json.dumps(ERR_PHRASES, ensure_ascii=False),
        ))
        page.screenshot(path=shot_path, full_page=True)
    except Exception as e:
        metrics = {"error": "%s: %s" % (type(e).__name__, e)}
        err = str(e)
        try:
            page.screenshot(path=shot_path, full_page=True)
        except Exception:
            pass
    finally:
        page.remove_listener("console", on_console)
        page.remove_listener("response", on_response)

    metrics["consoleErrors"] = console_errors
    metrics["badResponses"] = bad_responses
    return metrics, err


def log(msg):
    """控制台一律输出 ASCII —— 本机控制台是 GBK，直接 print 中文会 UnicodeEncodeError。"""
    print(str(msg).encode("ascii", "replace").decode("ascii"))


def rel_to_repo(path):
    """截图路径转成「相对仓库根」的 POSIX 风格。

    ⚠ `os.path.relpath` 在**跨盘**时会抛 ValueError
    （path is on mount 'C:', start on mount 'D:'）——
    用 `--out` 指向别的盘（如 %TEMP%）取证时必炸。
    跨盘时退化成绝对路径，不影响取证本身。
    """
    try:
        return os.path.relpath(path, REPO).replace("\\", "/")
    except ValueError:
        return os.path.abspath(path).replace("\\", "/")


def main():
    global FRONT
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=DEFAULT_SHOTS)
    ap.add_argument("--json", default=DEFAULT_JSON)
    ap.add_argument("--tmp", default=os.environ.get("TEMP", "."))
    ap.add_argument("--front", default=FRONT,
                    help="前端地址；打发布候选产物时传 `vite preview` 的端口（如 http://127.0.0.1:4173）")
    args = ap.parse_args()

    FRONT = args.front
    print("[front] %s" % FRONT)

    if not os.path.isdir(args.out):
        os.makedirs(args.out)

    results = []
    failures = []

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, args=["--force-color-profile=srgb"])

        # 1) 各角色登录一次，存 storage_state
        states = {}
        for role in ("admin", "parent", "kid"):
            try:
                states[role] = make_state(browser, role, args.tmp)
                log("[login] %s OK" % role)
            except Exception as e:
                log("[login] %s FAILED: %s" % (role, e))
                failures.append("login %s: %s" % (role, e))

        # 2) 逐视口 × 逐页取证
        for vp in VIEWPORTS:
            for code, group, name, path, role in PAGES:
                shot = os.path.join(args.out, "%s-%s-%dx%d-%s.png" % (code, vp["key"], vp["w"], vp["h"], name))
                # 只有「登录页」本身（C01/C02）用**未登录**上下文；
                # 其余孩子端页面必须带 kid 的 storage_state，
                # 否则会跳回登录页 —— 截出来的全是登录页，却**看起来很正常**。
                state = None if code in ("C01", "C02") else states.get(role)
                ctx = browser.new_context(
                    viewport={"width": vp["w"], "height": vp["h"]},
                    storage_state=state,
                    device_scale_factor=1,
                )
                page = ctx.new_page()
                url = FRONT + path
                # C02 要先跑完步骤①（家庭码 → 选孩子），截图才是「步骤②」的样子
                prepare = kid_step1 if code == "C02" else None
                metrics, err = capture(page, url, shot, prepare)
                ctx.close()

                # **落到登录页 = 这次取证是假的**（未登录 / 登录态失效 / 路由守卫踢回）。
                # 没有这条判据时，一屏屏「登录页」会被当成正常页面混进报告 ——
                # 2026-11-05 实测踩过：admin 登录失败 ⇒ 9 张管理端截图全是登录页，
                # 而每一张的「横向溢出 / 控制台错误 / 4xx」指标都是 0，看起来完全正常。
                landed = (metrics.get("url") or "")
                if "/login" in landed and code not in ("C01", "C02"):
                    metrics["redirectedToLogin"] = True
                    err = err or ("页面落到登录页（%s）：未登录 / 登录态失效 / 路由守卫踢回" % landed)

                rec = {
                    "code": code, "group": group, "name": name, "path": path,
                    "role": role, "viewport": vp["key"], "viewportLabel": vp["label"],
                    "width": vp["w"], "height": vp["h"],
                    "shot": rel_to_repo(shot),
                    "shotBytes": os.path.getsize(shot) if os.path.isfile(shot) else 0,
                    "metrics": metrics,
                }
                results.append(rec)
                if err:
                    failures.append("%s %s: %s" % (code, vp["key"], err))
                m = metrics
                log("[shot] %s %s overflow=%s console=%d bad=%d tdEmpty=%s/%s err=%d bytes=%d" % (
                    code, vp["key"],
                    m.get("overflow"), len(m.get("consoleErrors") or []),
                    len(m.get("badResponses") or []),
                    m.get("tdEmpty"), m.get("tdTotal"),
                    len(m.get("errHits") or []), rec["shotBytes"]))

        browser.close()

    payload = {
        "generatedAt": time.strftime("%Y-%m-%d %H:%M:%S"),
        "front": FRONT, "backend": BACKEND,
        "viewports": VIEWPORTS, "pages": PAGES and [
            {"code": c, "group": g, "name": n, "path": pa, "role": r} for c, g, n, pa, r in PAGES
        ],
        "results": results,
    }
    with open(args.json, "w", encoding="utf-8", newline="\n") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)

    # Markdown 表格（直接粘进报告源稿）
    md = []
    md.append("| 编号 | 页面 | 路径 | 视口 | 横向溢出 | 主按钮 | 表格/表头 | 数据列空白 | 单元格链接/按钮 | 表单布局 | 控制台错误 | 4xx/5xx | R-04 命中 | 业务错误提示 |")
    md.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
    for r in results:
        m = r["metrics"]
        md.append("| %s | %s | `%s` | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |" % (
            r["code"], r["name"], r["path"], r["viewport"],
            ("**%d px**" % m["overflow"]) if m.get("overflow") else "0",
            m.get("primaryBtn", "-"),
            "%s / %s" % (m.get("tables", "-"), m.get("thead", m.get("theads", "-"))),
            ("%s / %s" % (m.get("tdEmpty", "-"), m.get("tdTotal", "-"))),
            "%s / %s" % (m.get("tdLink", "-"), m.get("tdBtn", "-")),
            ("H%s V%s" % (m.get("formHorizontal", 0), m.get("formVertical", 0))),
            len(m.get("consoleErrors") or []),
            len(m.get("badResponses") or []),
            ("、".join(m.get("r04Hits") or []) or "无"),
            ("、".join(m.get("errHits") or []) or "无"),
        ))
    md_path = os.path.splitext(args.json)[0] + "-实测表.md"
    with open(md_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(md) + "\n")

    print("")
    log("screenshots -> %s" % args.out)
    log("json        -> %s" % args.json)
    log("md table    -> %s" % md_path)
    if failures:
        log("FAILURES (%d):" % len(failures))
        for f in failures:
            log("  - " + f)
        return 1
    log("ALL DONE")
    return 0


if __name__ == "__main__":
    sys.exit(main())
