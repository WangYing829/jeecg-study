#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""报告图片内嵌预处理：把 Markdown 源稿里的本地图片内嵌成 base64，再交给报告生成器渲染。

为什么要这一步：
  报告生成器（2026103001）只做 Markdown → HTML 渲染，**不会**处理图片。
  如果源稿里写相对路径，产物 HTML 必须与图片目录一起分发才算完整，
  这违反「主产物自包含」的要求；写绝对路径又会把机器路径写进交付物。
  所以在**生成之前**把本地图片转成 data URI，产物就是单文件、可直接发给别人。

用法：
  python 2026103102-报告图片内嵌.py <源稿.md> <输出.html>

规则：
  * 图片路径写在 HTML 的 <img src="..."> 里（推荐）或 Markdown 的 ![alt](path) 里；
  * 以 http:// / https:// / data: 开头的**跳过**（外链与已内联的都不动）；
  * 相对路径按**源稿所在目录**解析；绝对路径直接使用；
  * 找不到文件 → 报错退出（退出码 2），不静默生成缺图的报告。
"""

import base64
import mimetypes
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
GEN = os.path.join(HERE, "2026103001-报告生成器.py")

# <img ... src="PATH" ...>  —— 只替换 src 的值，标签其余部分原样保留
RE_HTML_IMG = re.compile(r'(<img\b[^>]*?\bsrc=")([^"]+)(")', re.I)
# ![alt](PATH)
RE_MD_IMG = re.compile(r'!\[([^\]]*)\]\(([^)\s]+)')

SKIP_PREFIX = ("http://", "https://", "data:", "//")


def resolve(path, base_dir):
    if os.path.isabs(path):
        return path
    return os.path.normpath(os.path.join(base_dir, path))


def to_data_uri(path):
    mime = mimetypes.guess_type(path)[0] or "image/png"
    with open(path, "rb") as f:
        blob = f.read()
    return "data:%s;base64,%s" % (mime, base64.b64encode(blob).decode("ascii"))


def embed(text, base_dir):
    """把文稿里的本地图片路径替换为 data URI，返回 (新文本, 内嵌张数, 缺失列表)。"""
    missing = []
    count = [0]

    def repl_html(m):
        path = m.group(2).strip()
        if path.lower().startswith(SKIP_PREFIX):
            return m.group(0)
        real = resolve(path, base_dir)
        if not os.path.isfile(real):
            missing.append(path)
            return m.group(0)
        count[0] += 1
        return m.group(1) + to_data_uri(real) + m.group(3)

    def repl_md(m):
        path = m.group(2).strip()
        if path.lower().startswith(SKIP_PREFIX):
            return m.group(0)
        real = resolve(path, base_dir)
        if not os.path.isfile(real):
            missing.append(path)
            return m.group(0)
        count[0] += 1
        return "![%s](%s)" % (m.group(1), to_data_uri(real))

    out = RE_HTML_IMG.sub(repl_html, text)
    out = RE_MD_IMG.sub(repl_md, out)
    return out, count[0], missing


def main():
    if len(sys.argv) != 3:
        print("用法: python 2026103102-报告图片内嵌.py <源稿.md> <输出.html>")
        return 1
    src, out = sys.argv[1], sys.argv[2]
    if not os.path.isfile(src):
        print("源稿不存在: %s" % src)
        return 2
    if not os.path.isfile(GEN):
        print("找不到报告生成器: %s" % GEN)
        return 2

    with open(src, "r", encoding="utf-8") as f:
        raw = f.read()

    base_dir = os.path.dirname(os.path.abspath(src))
    embedded, n, missing = embed(raw, base_dir)

    if missing:
        for p in sorted(set(missing)):
            print("图片缺失: %s" % p)
        return 2

    tmp_fd, tmp_md = tempfile.mkstemp(suffix=".md")
    os.close(tmp_fd)
    try:
        with open(tmp_md, "w", encoding="utf-8", newline="\n") as f:
            f.write(embedded)
        rc = subprocess.call([sys.executable, "-X", "utf8", GEN, tmp_md, out])
    finally:
        try:
            os.unlink(tmp_md)
        except OSError:
            pass

    if rc != 0:
        return rc
    print("内嵌图片 %d 张" % n)
    return 0


if __name__ == "__main__":
    sys.exit(main())
