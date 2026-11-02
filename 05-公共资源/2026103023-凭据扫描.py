# -*- coding: utf-8 -*-
"""凭据扫描闸门（入库公开仓库前的最后一道锁）。

为什么需要这个脚本
------------------
本项目要推送到**公开** GitHub 仓库。上游 JeecgBoot 自带的默认口令
（``root`` / ``postgres`` / ``123456`` / ``dd05f1c54d63749eda95f9fa6d49v442a``）
本来就在上游公开仓库里，推上去**不构成新增泄漏**；
但**本机真实口令**一旦写进入库文件，就是**新增的凭据泄漏**——
改代码不闭环，**轮换口令**才闭环。

2026-11-02 实测：推仓库前扫描命中 3 处本机真实口令
（``application-dev.yml`` / ``docker-compose.yml`` / study 模块 ``application-test.yml``），
已全部改为环境变量注入（``${DB_PASSWORD:root}``、``${MYSQL_ROOT_PASSWORD:-root}``）。
本脚本就是把那次人工排查**固化成闸门**，防回归。

判定口径
--------
1. **只看会入库的文件**：
   - 仓库普通文件（排除基座目录、``.git``、``node_modules``、``target`` 等）；
   - 基座目录里**只**看 overlay 清单中的文件——本地基座是完整上游源码，
     但入库的只有相对 v3.9.2 改动的那些文件。
2. **值**判定（先判「是不是凭据」，再判「是不是明文」）：
   - ``${VAR}`` / ``${VAR:默认值}`` → 占位符，安全；
     但**默认值本身要再判一次**——``${MYSQL_ROOT_PASSWORD:-真实口令}`` 仍然是泄漏；
   - ``$(...)`` / ``{{...}}`` / ``{%...%}`` → 其他语言的插值，安全；
   - 含中文的值 → 上游占位文案（如「请仔细核对配置是否符合实际项目情况」），安全；
   - 命中**上游公开默认值白名单** → 安全（不是本项目引入的泄漏）；
   - **不像凭据的字面量**（``??``、``$env``、``getToken()``、``src/App.vue:41-99``）
     → 安全。只有「字母 + 数字混合」或「长且多字符类」的值才算疑似凭据。
3. **源码文件**（.java/.ts/.js/.vue/.py）只认**引号包裹的字面量**，
   避免把 ``getToken()`` 这类函数调用误判成口令。

规则编号
--------
- ``R1`` 配置/脚本里的 ``key: value`` 明文口令
- ``R2`` ``${VAR:默认值}`` 里的**默认值**是明文口令
- ``R3`` Markdown 表格里的明文口令（含中文「密码 / 口令 / 密钥」列）
- ``R4`` mysql 命令行 ``-p<口令>``
- ``R5`` 源码里的引号口令字面量
- ``R6`` **散文 / 行内代码块**里的「字母@数字」形态口令
  （前 5 条都是**结构化**规则，抓不到「文档里随手写一句 ``口令@2026``」这种）

自检
----
内置**扫描有效性自检**：往规则引擎里塞几条合成的假口令，必须被判为命中；
同时塞几条安全的写法，必须被放过。规则一旦写坏就会「扫描集合非空但规则全失效 ⇒ 假绿」。

用法
----
    python -X utf8 .\\05-公共资源\\2026103023-凭据扫描.py

退出码
------
0 = 未发现明文凭据；2 = 发现明文凭据；1 = 执行失败。
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join(ROOT, "02-代码开发", "05-源码", "01-JeecgBoot基座")
OVERLAY_LIST = os.path.join(BASE, "基座overlay-files.txt")
BASE_MANIFESTS = ["基座overlay-files.txt", "基座改动清单.md"]
SELF = os.path.abspath(__file__)

SKIP_DIRS = {
    ".git", "node_modules", "target", "__pycache__", ".idea", ".vscode",
    "dist", ".pnpm-store", ".mvn", ".venv", "venv",
}

# 配置 / 文档类：宽口径判定（key: value 形式）
CONFIG_SUFFIX = {".yml", ".yaml", ".properties", ".conf", ".ini", ".toml",
                 ".xml", ".sql", ".md", ".txt"}
# 脚本类：命令行口令 + key: value
SCRIPT_SUFFIX = {".ps1", ".sh", ".bat", ".cmd"}
# 源码类：只认引号字面量
SOURCE_SUFFIX = {".java", ".ts", ".js", ".vue", ".py"}
# 无后缀但按配置处理
CONFIG_NAMES = {".gitignore", ".gitattributes", ".editorconfig", "Dockerfile", "Makefile"}

# 敏感键名（英文，按「键里包含这些词」判定）
SENSITIVE_TOKENS = (
    "password", "passwd", "pwd", "secret", "accesskey", "access-key",
    "accesstoken", "access-token", "appsecret", "app-secret",
    "clientsecret", "client-secret", "apisecret", "api-secret",
    "signature", "pepper", "token", "apikey", "api-key",
)
# 中文敏感键名（只用于 Markdown 表格这类结构化上下文）
SENSITIVE_CN = ("密码", "口令", "密钥")

# 上游公开默认值 + 本项目明确标注为占位的默认值 → 不算本项目引入的泄漏
ALLOWLIST = {
    # —— 上游 JeecgBoot v3.9.2 自带（上游公开仓库里本来就有）——
    "root", "postgres", "jeecg1314", "123456", "xxxxxx", "test", "changeme",
    "dd05f1c54d63749eda95f9fa6d49v442a",
    # —— 本项目标注为「占位 / 请覆盖」的默认值 ——
    "jeecg-study-dev-pepper-please-override",
    "jeecg-study-ut-pepper-2026",
    # —— 演示账号口令（种子数据里的公开演示凭据，与上游 admin/123456 同类）——
    "study@2026",
}

# key: value / key=value（键必须是干净标识符，且前面不是标识符字符）
ASSIGN_RE = re.compile(
    r"(?<![A-Za-z0-9_.])(?P<key>[A-Za-z_][A-Za-z0-9_.\-]{0,79})\s*[:=]\s*(?P<value>.+)$"
)
# 行内任意位置的 Spring 占位符 ${VAR} / ${VAR:默认值} / ${VAR:-默认值}
SPRING_ANY_RE = re.compile(
    r"\$\{[A-Za-z_][A-Za-z0-9_.\-]*(?::-?(?P<default>[^}]*))?\}"
)
# 整个值就是 Spring 占位符
SPRING_RE = re.compile(r"^\$\{([A-Za-z_][A-Za-z0-9_.\-]*)(?::-?(.*))?\}$")
# Markdown 表格行：| 键 | 值 |
TABLE_RE = re.compile(r"^\s*\|(?P<c0>[^|]*)\|(?P<c1>[^|]*)\|")
# mysql 命令行 -p<口令>
MYSQL_PWD_RE = re.compile(r"(?i)mysql[^\n]*?-p([A-Za-z0-9!@#$%^&*_+.\-]{4,})")
# 源码里的引号字面量
SOURCE_LITERAL_RE = re.compile(
    r"(?i)(?:password|passwd|secret|token|pepper|apikey|api_key|signature)"
    r"\w{0,20}\s*[:=]\s*[\"'](?P<value>[^\"']{6,})[\"']"
)
# 表格值必须是「单个 token」：不含空白、路径符、赋值符、括号
TOKEN_RE = re.compile(r"^[^\s/\\:=(),;\"'`\[\]{}<>|]{4,}$")
# R6：散文 / 行内代码块里的「字母@数字」形态口令
#     形态收得很紧（≤16 位字母 + **恰好 4 位**数字），否则会误伤：
#       ResourceUrlEncodingRequestWrapper@1f99f8d4 / ShiroHttpServletRequest@69203563
#         （Spring bean 名 = 长类名 + 8 位十六进制哈希；类名 >16 位，数字 ≠4 位）
#       zhang@163 / jeecgos@163（上游演示邮箱，@ 后只有 3 位）
#       doe@example / winter@jeecg（@ 后是字母）
# 真正要抓的是像 zzzself@2026 这种「短词@4 位数字」的形态。
MAIL_PWD_RE = re.compile(r"(?<![A-Za-z0-9_])(?P<value>[A-Za-z]{4,16}@[0-9]{4})(?![0-9A-Za-z])")
CJK_RE = re.compile(r"[\u4e00-\u9fff]")


def strip_quotes(text):
    """去掉包裹引号（成对）。"""
    t = text.strip()
    for q in ("'", '"', "`"):
        if len(t) >= 2 and t.startswith(q) and t.endswith(q):
            return t[1:-1].strip()
    return t


def looks_like_secret(value):
    """像不像一个真凭据。

    这一层是**降噪**用的：上游 yml 里的 ``??``、文档里的 ``src/App.vue:41-99``、
    脚本里的 ``$env`` 都不是口令，但形状很接近，靠正则很难一刀切干净。
    判据：字母 + 数字混合，或长度 ≥12 且字符类 ≥3。
    """
    v = (value or "").strip()
    if len(v) < 6:
        return False
    has_digit = any(c.isdigit() for c in v)
    has_alpha = any(c.isalpha() for c in v)
    if has_digit and has_alpha:
        return True
    classes = sum([
        any(c.islower() for c in v),
        any(c.isupper() for c in v),
        any(c.isdigit() for c in v),
        any(not c.isalnum() for c in v),
    ])
    return len(v) >= 12 and classes >= 3


def evaluate_value(raw):
    """判定一个值是不是「明文凭据」。

    返回 (命中?, 值, 原因)。
    """
    v = (raw or "").strip()
    if not v:
        return False, "", "空值"

    # 行尾注释（` # ...`）——值本身不含空格时不会被误伤
    v = re.sub(r"\s+#.*$", "", v).strip()
    v = strip_quotes(v)
    if not v:
        return False, "", "空值"

    # Spring 占位符：本身安全，但**默认值**要再判一次
    m = SPRING_RE.match(v)
    if m:
        default = m.group(2)
        if default is None:
            return False, "", "占位符"
        return evaluate_value(default)

    # 其他语言的插值
    if any(tok in v for tok in ("$(", "{{", "{%", "%(", "${")):
        return False, "", "插值"

    # 含中文 → 上游占位文案
    if CJK_RE.search(v):
        return False, "", "占位文案"

    # 上游公开默认值白名单
    if v.lower() in ALLOWLIST:
        return False, "", "白名单"

    # 不像凭据的字面量
    if not looks_like_secret(v):
        return False, "", "不像凭据"

    return True, v, "明文字面量"


def key_is_sensitive(key, allow_cn=False):
    k = (key or "").strip().strip("'\"").lower()
    if not k:
        return False
    if any(tok in k for tok in SENSITIVE_TOKENS):
        return True
    if allow_cn and any(tok in k for tok in SENSITIVE_CN):
        return True
    return False


def scan_text(text, kind, path):
    """对单个文件内容跑规则，返回命中列表。"""
    findings = []

    # R4：mysql 命令行 -p<口令>（所有类型都查）
    for m in MYSQL_PWD_RE.finditer(text):
        hit, val, _ = evaluate_value(m.group(1))
        if hit:
            line_no = text.count("\n", 0, m.start(1)) + 1
            findings.append((path, line_no, "R4", "mysql 命令行口令", val))

    if kind == "source":
        # R5：源码里的引号字面量
        for m in SOURCE_LITERAL_RE.finditer(text):
            hit, val, _ = evaluate_value(m.group("value"))
            if hit:
                line_no = text.count("\n", 0, m.start("value")) + 1
                findings.append((path, line_no, "R5", "源码口令字面量", val))
        return findings

    lines = text.splitlines()
    for lineno, line in enumerate(lines, start=1):
        # R2：行内 ${VAR:默认值} 的默认值
        for m in SPRING_ANY_RE.finditer(line):
            default = m.group("default")
            if default is None:
                continue
            hit, val, _ = evaluate_value(default)
            if hit:
                findings.append((path, lineno, "R2", "占位符默认值是明文口令", val))

        # R1：key: value
        for m in ASSIGN_RE.finditer(line):
            start = m.start("key")
            # ${VAR:默认值} 里的变量名不是配置键
            if start > 0 and line[start - 1] == "$":
                continue
            if not key_is_sensitive(m.group("key")):
                continue
            value = m.group("value")
            if "${" in value:
                # 值本身是占位符 → 由 R2 负责判定
                continue
            hit, val, _ = evaluate_value(value)
            if hit:
                findings.append((path, lineno, "R1", "配置明文口令", val))

        # R3：Markdown 表格（中文键也认）
        if path.lower().endswith(".md"):
            tm = TABLE_RE.match(line)
            if tm and key_is_sensitive(tm.group("c0"), allow_cn=True):
                cell = strip_quotes(tm.group("c1"))
                if not TOKEN_RE.match(cell):
                    continue
                hit, val, _ = evaluate_value(cell)
                if hit:
                    findings.append((path, lineno, "R3", "文档表格明文口令", val))

        # R6：散文 / 行内代码块里的「字母@数字」形态口令
        #     前 5 条规则都是**结构化**的（key: value / 表格 / 命令行 / 引号），
        #     抓不到「文档里随手写一句 `git grep xxx@2026`」这种。
        for m in MAIL_PWD_RE.finditer(line):
            hit, val, _ = evaluate_value(m.group("value"))
            if hit:
                findings.append((path, lineno, "R6", "文档里的明文口令字面量", val))

    return findings


def classify(path):
    """返回文件类别：config / script / source / None（不扫）。"""
    base = os.path.basename(path)
    suffix = os.path.splitext(base)[1].lower()
    if base.startswith(".env"):
        return "config"
    if base in CONFIG_NAMES:
        return "config"
    if suffix in CONFIG_SUFFIX:
        return "config"
    if suffix in SCRIPT_SUFFIX:
        return "script"
    if suffix in SOURCE_SUFFIX:
        return "source"
    return None


def read_text(path):
    """读文本文件；二进制或读不动的直接跳过。"""
    try:
        with open(path, "rb") as f:
            raw = f.read()
    except OSError:
        return None
    if b"\x00" in raw[:4096]:
        return None
    for enc in ("utf-8-sig", "utf-8", "gbk"):
        try:
            return raw.decode(enc)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", errors="ignore")


def iter_repo_files():
    """仓库普通文件（整棵基座目录排除，基座单独按 overlay 清单扫）。"""
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = sorted(d for d in dirnames if d not in SKIP_DIRS)
        if os.path.abspath(dirpath) == os.path.abspath(BASE):
            dirnames[:] = []
            continue
        for fn in sorted(filenames):
            yield os.path.join(dirpath, fn)


def iter_base_files():
    """基座目录里**会入库**的文件 = overlay 清单 + 两份清单产物。"""
    out = []
    if os.path.isfile(OVERLAY_LIST):
        with open(OVERLAY_LIST, "r", encoding="utf-8") as f:
            for line in f:
                rel_path = line.strip()
                if not rel_path:
                    continue
                p = os.path.join(BASE, rel_path.replace("/", os.sep))
                if os.path.isfile(p):
                    out.append(p)
    for name in BASE_MANIFESTS:
        p = os.path.join(BASE, name)
        if os.path.isfile(p):
            out.append(p)
    return out


def rel(path):
    try:
        return os.path.relpath(path, ROOT).replace(os.sep, "/")
    except ValueError:
        return path


def self_test():
    """扫描有效性自检：合成假口令必须被判为命中，安全的写法必须被放过。

    没有这一步，规则一旦写坏就会「扫描集合非空但规则全失效 ⇒ 假绿」。
    """
    samples = [
        # (类别, 路径, 文本, 期望命中?)
        ("config", "<自检>.md", "| 密码 | zzz-self-test-9f3a2b |", True),
        ("config", "<自检>.md", "| 密码 | 环境变量 `DB_PASSWORD` 注入 |", False),
        ("config", "<自检>.yml", "          password: zzz-self-test-9f3a2b", True),
        ("config", "<自检>.yml", "      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-zzz-self-test-9f3a2b}", True),
        ("config", "<自检>.yml", "          password: ${DB_PASSWORD:root}", False),
        ("config", "<自检>.yml", "          password: root", False),
        ("config", "<自检>.yml", "          secretKey: 请仔细核对配置是否符合实际项目情况", False),
        ("config", "<自检>.yml", "          apiKey: ??", False),
        ("script", "<自检>.ps1", "$raw = & $mysql -uroot '-pzzz-self-test-9f3a2b' -D db", True),
        ("script", "<自检>.ps1", "$raw = & $mysql -uroot (\"-p\" + $DbPassword) -D db", False),
        ("script", "<自检>.ps1", "& $mysql -u root -p$env:MYSQL_PWD $db", False),
        ("source", "<自检>.java", 'String password = "zzz-self-test-9f3a2b";', True),
        ("source", "<自检>.java", "String token = getToken();", False),
        ("config", "<自检>.md", "复查口令 `zzzself@2026` 是否还有残留", True),
        ("config", "<自检>.md", "演示账号 `study@2026`（白名单）", False),
        ("config", "<自检>.sql", "`ResourceUrlEncodingRequestWrapper@1f99f8d4` bean 名", False),
        ("config", "<自检>.sql", "上游演示邮箱 `zhang@163`", False),
    ]
    problems = []
    for kind, path, line, expect in samples:
        found = scan_text(line, kind, path)
        got = bool(found)
        if got != expect:
            problems.append("自检样本判定错误（期望命中=%s，实际=%s）：%s"
                            % (expect, got, line.strip()))
    return problems


def main():
    print("=" * 62)
    print("凭据扫描闸门 · 2026103023")
    print("=" * 62)

    problems = self_test()
    if problems:
        print("[自检] FAIL 规则引擎失效：")
        for p in problems:
            print("       " + p)
        return 1
    print("[自检] PASS 规则引擎能识别合成口令，也能放过占位符/白名单/非凭据字面量")

    findings = []
    skipped = 0

    # 1) 仓库普通文件
    repo_count = 0
    for path in iter_repo_files():
        if os.path.abspath(path) == SELF:
            continue
        kind = classify(path)
        if kind is None:
            skipped += 1
            continue
        text = read_text(path)
        if text is None:
            skipped += 1
            continue
        repo_count += 1
        findings.extend(scan_text(text, kind, rel(path)))
    print("[1/3] 扫描仓库文件（已排除基座目录/构建产物）：%d 个" % repo_count)

    # 2) 基座 overlay 文件（真正会入库的那部分）
    base_count = 0
    for path in iter_base_files():
        kind = classify(path)
        if kind is None:
            skipped += 1
            continue
        text = read_text(path)
        if text is None:
            skipped += 1
            continue
        base_count += 1
        findings.extend(scan_text(text, kind, rel(path)))
    print("[2/3] 扫描基座 overlay 文件（只扫会入库的）：%d 个" % base_count)

    if repo_count == 0 or base_count == 0:
        print("[3/3] FAIL 扫描集合为空（规则空转 = 假绿），请检查路径与清单")
        return 1
    print("[3/3] 扫描有效性：仓库 %d + 基座 %d = %d 个文件（非空，规则真实生效）"
          % (repo_count, base_count, repo_count + base_count))

    if findings:
        print("-" * 62)
        print("发现明文凭据 %d 处：" % len(findings))
        for path, lineno, rule, why, val in findings:
            print("  [%s] %s:%d" % (rule, path, lineno))
            print("        %s：%s" % (why, val))
        print("-" * 62)
        print("FAIL 请改为环境变量注入（${DB_PASSWORD:root} 形式），再重跑本脚本")
        return 2

    print("-" * 62)
    print("ALL PASS 未发现明文凭据（跳过 %d 个非文本/非扫描文件）" % skipped)
    return 0


if __name__ == "__main__":
    sys.exit(main())
