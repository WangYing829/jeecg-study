# -*- coding: utf-8 -*-
"""
交付物静态校验：对全部 HTML 报告做结构自检，并核对源稿与产物的章节对齐。

检查项：
  1. 锚点链接全部有对应 id（无断链）
  2. section id 唯一
  3. 关键标签开闭配平
  4. 无 BOM、无乱码（U+FFFD）
  5. 表格有 thead/tbody（移动端卡片化 JS 依赖）
  6. 导航含「页面顶部」「页面底部」
  7. 主色变量在 :root 内（自定义属性不得裸写在 style 顶层）
  8. 章节数 = 导航项数
  9. **章节标题对齐**：每个 section 的 sec-head 标题 == 其内容首个标题
     （抓「内容错位一格」——META 多一条/少一条 section 就会在这里炸）
 10. **源稿对齐**：META.sections 数量与顺序 == 正文 `##` 标题数量与顺序
     （抓根因：模板占位标题被误写成 `##`、或 META 漏写一条）

用法：python 2026103013-交付物校验.py
退出码：0 = 全过；1 = 有失败
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 产物 html。**源稿 md 与产物 html 同基名同目录**（命名规约：内容相同 → 同一套命名），
# 所以源稿直接由产物推导，脚本里不再手写第二份路径 —— 规则由结构保证，跑不偏。
HTML_FILES = [
    r"01-需求迭代/02-需求文档/01-产品需求说明书.html",
    r"01-需求迭代/03-设计规范/01-产品设计规范.html",
    r"02-代码开发/01-设计文档/01-系统设计说明书.html",
    r"02-代码开发/02-数据库/01-数据库设计说明书.html",
    r"02-代码开发/03-接口文档/01-接口设计说明书.html",
    r"02-代码开发/04-前端页面/01-前端页面清单.html",
    r"03-测试验证/01-测试文档/01-测试大纲.html",
    r"03-测试验证/02-测试用例/01-测试用例.html",
    r"04-运营部署/01-部署文档/01-部署方案.html",
    r"04-运营部署/02-运维文档/01-运维手册.html",
    r"03-测试验证/01-测试文档/01-P0-P4验证报告.html",
    r"03-测试验证/01-测试文档/02-浏览器实测取证报告.html",
    r"04-运营部署/04-用户手册/01-系统功能使用说明.html",
    r"04-运营部署/05-技术手册/01-系统开发使用说明.html",
]

# (源稿 md, 产物 html)
PAIRS = [(p[:-len(".html")] + ".md", p) for p in HTML_FILES]

TAGS = ["div", "aside", "main", "script", "style", "table", "thead", "tbody",
        "tr", "th", "td", "ul", "ol", "li", "p", "h2", "h3", "h4", "h5",
        "footer", "header", "section", "button", "a", "strong", "em", "code",
        "pre", "blockquote", "hr", "br"]


def norm(t):
    """归一化标题：去编号前缀、去空白、去强调符号。"""
    t = re.sub(r"^\s*\d+(\.\d+)*\s*[、.．]?\s*", "", t)
    t = re.sub(r"[*`\s　]+", "", t)
    return t


def same_chapter(nav, content):
    """导航标题与内容标题是否指同一章。

    导航常常是**缩写**标签（nav「模块③ 趣味背单词」/ 正文「模块③ 趣味高效背单词」），
    所以不能用相等判断。判据：二者互为前缀，或有足够长的公共前缀。

    真正要抓的是「导航 A → 内容 B」这种**完全无关**的错位。
    """
    a, b = norm(nav), norm(content)
    if not a or not b:
        return True
    if a == b or a.startswith(b) or b.startswith(a):
        return True
    common = 0
    for x, y in zip(a, b):
        if x != y:
            break
        common += 1
    return common >= max(2, 0.4 * min(len(a), len(b)))


def check_source(md_rel):
    """检查源稿：META.sections 与正文 `##` 是否数量与顺序一致。"""
    fails = []
    p = os.path.join(ROOT, md_rel)
    if not os.path.isfile(p):
        return ["源稿缺失: %s" % md_rel]
    with open(p, "r", encoding="utf-8") as f:
        text = f.read()

    m = re.search(r"<!--META\s*(\{.*?\})\s*-->", text, re.S)
    if not m:
        return ["源稿缺 META 块"]
    try:
        meta = json.loads(m.group(1))
    except Exception as e:
        return ["META 不是合法 JSON: %s" % e]

    secs = meta.get("sections", [])
    heads = re.findall(r"^##\s+(.+?)\s*$", text, re.M)

    if len(secs) != len(heads):
        fails.append("源稿章节数错位：META.sections=%d，正文 `##`=%d —— "
                     "内容会整体错位 %d 格"
                     % (len(secs), len(heads), len(heads) - len(secs)))
        for i in range(max(len(secs), len(heads))):
            nav = secs[i]["nav"] if i < len(secs) else "<<META 缺>>"
            hd = heads[i] if i < len(heads) else "<<正文缺 ##>>"
            if norm(nav) != norm(hd):
                fails.append("    [%02d] META=%-18s 正文=%s" % (i + 1, nav, hd))
    else:
        for i, (s, hd) in enumerate(zip(secs, heads)):
            if not same_chapter(s["nav"], hd):
                fails.append("源稿第 %02d 节标题不一致：META=%s 正文=%s"
                             % (i + 1, s["nav"], hd))
    return fails


def check_html(path, rel):
    fails = []
    with open(path, "rb") as f:
        raw_bytes = f.read()
    raw = raw_bytes.decode("utf-8")

    # 4a. BOM
    if raw_bytes.startswith(b"\xef\xbb\xbf"):
        fails.append("含 BOM（应为 UTF-8 无 BOM）")
    # 4b. 乱码
    if "\ufffd" in raw:
        n = raw.count("\ufffd")
        idx = raw.find("\ufffd")
        ctx = raw[max(0, idx - 40):idx + 40].replace("\n", " ")
        fails.append("含 %d 个乱码字符 U+FFFD，附近: ...%s..." % (n, ctx))

    # 2. id 唯一
    ids = re.findall(r'\sid="([^"]+)"', raw)
    dup = sorted(set(i for i in ids if ids.count(i) > 1))
    if dup:
        fails.append("重复 id: %s" % ", ".join(dup[:8]))

    # 1. 锚点断链
    hrefs = re.findall(r'href="#([^"]+)"', raw)
    broken = sorted(set(h for h in hrefs if h and h not in ids))
    if broken:
        fails.append("断链锚点 %d 个: %s" % (len(broken), ", ".join(broken[:8])))

    # 3. 标签配平
    unbalanced = []
    for t in TAGS:
        o = len(re.findall(r"<%s(?=[\s>])" % t, raw))
        c = len(re.findall(r"</%s>" % t, raw))
        if t in ("br", "hr"):
            continue
        if o != c:
            unbalanced.append("%s(open=%d,close=%d)" % (t, o, c))
    if unbalanced:
        fails.append("标签未配平: " + "; ".join(unbalanced))

    # 5. 表格结构
    ntbl = len(re.findall(r"<table(?=[\s>])", raw))
    nthead = len(re.findall(r"<thead(?=[\s>])", raw))
    if ntbl and nthead < ntbl:
        fails.append("存在无 thead 的表格（移动端卡片化 JS 依赖 thead 列名）")

    # 6. 导航上下跳转
    if "页面顶部" not in raw:
        fails.append("导航缺「页面顶部」")
    if "页面底部" not in raw:
        fails.append("导航缺「页面底部」")

    # 7. 自定义属性必须在 :root 内
    m = re.search(r"<style>(.*?)</style>", raw, re.S)
    if m:
        css = m.group(1)
        depth = 0
        for line in css.split("\n"):
            stripped = line.strip()
            if depth == 0 and re.match(r"^--[a-zA-Z0-9-]+\s*:", stripped):
                fails.append("style 顶层裸写自定义属性（浏览器会忽略）: %s" % stripped[:60])
                break
            depth += line.count("{") - line.count("}")

    # 8. 章节数
    nsec = len(re.findall(r"<section(?=[\s>])", raw))
    nnav = len(re.findall(r'class="nav-link"', raw)) - 2  # 减去顶部/底部两条
    if nsec != nnav:
        fails.append("章节数(%d) 与导航项数(%d) 不一致" % (nsec, nnav))

    # 9. 章节标题对齐：sec-head 的标题必须等于该 section 内容里的首个标题
    sec_re = re.compile(
        r'<section id="([^"]+)">(.*?)(?=<section id="|</main>)', re.S)
    misaligned = []
    for sid, body in sec_re.findall(raw):
        head = re.search(r'<div class="sec-head">.*?<h3>(.*?)</h3>', body, re.S)
        if not head:
            continue
        nav_title = re.sub(r"<[^>]+>", "", head.group(1))
        rest = body[head.end():]
        first = re.search(r"<(p|h3|h4)(?:\s[^>]*)?>(.*?)</\1>", rest, re.S)
        if not first:
            continue
        content_title = re.sub(r"<[^>]+>", "", first.group(2))
        if not same_chapter(nav_title, content_title):
            misaligned.append("%s：导航「%s」→ 内容「%s」"
                              % (sid, nav_title.strip(), content_title.strip()))
    if misaligned:
        fails.append("章节内容错位 %d 处（导航标题 ≠ 内容标题）: %s"
                     % (len(misaligned), "; ".join(misaligned[:4])))

    return fails, len(raw_bytes), nsec, len(hrefs), ntbl, len(ids)


def main():
    total = 0
    missing = []
    for md_rel, html_rel in PAIRS:
        p = os.path.join(ROOT, html_rel)
        if not os.path.isfile(p):
            print("[MISS] %s" % html_rel)
            missing.append(html_rel)
            total += 1
            continue
        fails, size, nsec, nhref, ntbl, nids = check_html(p, html_rel)
        src_fails = check_source(md_rel)
        fails = fails + src_fails
        status = "PASS" if not fails else "FAIL"
        print("[%s] %s" % (status, html_rel))
        print("       size=%d B  sections=%d  anchors=%d  tables=%d  ids=%d"
              % (size, nsec, nhref, ntbl, nids))
        for f in fails:
            print("       - " + f)
        total += len(fails)
    print("")
    if total:
        print("TOTAL FAIL = %d" % total)
        return 1
    print("ALL PASS (%d files)" % len(PAIRS))
    return 0


if __name__ == "__main__":
    sys.exit(main())
