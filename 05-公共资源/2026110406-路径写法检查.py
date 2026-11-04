#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
jeecg-study · 闸门：路径写法体检（Linux 区分大小写 + 分斜杠才是分隔符）

为什么要有这道闸门
------------------
2026-11-04 的 CI 排查连续踩中**同一类**的三个坑，它们有一个共同特征：
**本地（Windows）全绿，CI（Linux）全红**，而且失败信息都指向下游，看不出真因。

| # | 写法 | Windows | Linux | 后果 |
|---|---|---|---|---|
| 1 | `ROOT = r"D:\\DevProject\\..."` | 能跑 | 找不到目录 | 闸门全灭 |
| 2 | `r"…\\studyrecordtablemapper.java"` 去 resolve `StudyRecordTableMapper.java` | 能找到（不区分大小写） | 找不到 | 断言假绿 / 假红 |
| 3 | `r"01-需求迭代\\02-需求文档\\01-产品需求说明书.html"` 去 replace 成正斜杠 | 能找到（反斜杠也是分隔符） | 找不到（反斜杠是普通字符） | 14 个交付物全 MISS |

第 3 条就是 CI 第 8 次 run 的失败点：闸门报 `[MISS] …`，
但那些 HTML **明明在 git 里**（`git ls-files` 能查到）⇒ 是**路径写法**的问题，不是文件缺失。

两项检查
--------
P1 **大小写**：Java 里**真正拿去做 `resolve(...)`** 的路径常量，
    在候选根目录下必须**大小写敏感**地存在。
    （只用于字符串比对的小写常量不算 —— `moduleRelative()` 的结果本来就是小写的。）
P2 **分隔符**：路径字面量（Java + Python）里的目录分隔符必须是**正斜杠**。
    判据按**语义**而不是猜正则：把反斜杠换成正斜杠后若真能指向仓库里的文件，
    它就是想指那个文件 ⇒ 用反斜杠写就是错的。

⚠ 写这道闸门时自己也会踩坑：**Windows 的 `os.path.isfile` 也不区分大小写**，
拿它当「精确匹配」检查会永远返回 True ⇒ 闸门形同虚设。
正解是**逐段拿 `os.listdir` 的真实目录项做精确比对**。

退出码：0 = 全部通过；2 = 有问题（CI 直接红）
"""

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

BASE = os.path.join(REPO_ROOT, "02-代码开发", "05-源码", "01-JeecgBoot基座")
MODULE = os.path.join(BASE, "jeecg-boot", "jeecg-boot-module", "jeecg-module-study")

# ---- P1 候选根目录：路径常量可能被 resolve 到这些根下 ----------------------------
CANDIDATE_ROOTS = [
    ("仓库根", REPO_ROOT),
    ("SQL 脚本目录", os.path.join(REPO_ROOT, "02-代码开发", "02-数据库", "03-脚本")),
    ("study 主源码根", os.path.join(MODULE, "src", "main", "java",
                                    "org", "jeecg", "modules", "study")),
    ("study 测试资源", os.path.join(MODULE, "src", "test", "resources")),
    ("前端源码根", os.path.join(BASE, "jeecgboot-vue3", "src")),
]

JAVA_SCAN_DIRS = [
    os.path.join(MODULE, "src", "main", "java"),
    os.path.join(MODULE, "src", "test", "java"),
]

PY_SCAN_DIRS = [
    os.path.join(REPO_ROOT, "05-公共资源"),
    os.path.join(REPO_ROOT, ".github"),
]

PATH_EXTS = (
    ".java", ".vue", ".ts", ".tsx", ".js", ".jsx", ".sql", ".md", ".html",
    ".json", ".yml", ".yaml", ".less", ".scss", ".xml", ".properties", ".py",
    ".sh", ".ps1", ".csv", ".txt",
)

CONST_RE = re.compile(
    r'\b(?:static\s+final\s+)?String\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*"([^"\\\n]{2,300})"')
RESOLVE_RE = re.compile(r'\.resolve\(\s*([A-Za-z_][A-Za-z0-9_]*|"[^"\\\n]*")\s*\)')
# ⚠ 字面量里**可能含反斜杠**（那正是 P2 要抓的东西），所以字符类里不能排除 `\`。
LIT_RE = re.compile(r'"([^"\n]{2,300})"')


def decode_unicode_escapes(s):
    """把 \\uXXXX 转义还原成真字符（非 raw 字符串里的 \\u 是 Unicode 转义，不是分隔符）。"""
    return re.sub(r"\\u([0-9a-fA-F]{4})",
                  lambda m: chr(int(m.group(1), 16)), s)


def walk_segments_ci(root, rel):
    """大小写**不敏感**地逐段下钻，返回命中的真实目录项名列表；中途找不到返回 None。"""
    parts = [p for p in rel.replace("\\", "/").split("/") if p and p != "."]
    cur = root
    walked = []
    for p in parts:
        if not os.path.isdir(cur):
            return None
        try:
            entries = os.listdir(cur)
        except OSError:
            return None
        hit = None
        for e in entries:
            if e.lower() == p.lower():
                hit = e
                break
        if hit is None:
            return None
        walked.append(hit)
        cur = os.path.join(cur, hit)
    return walked


def exists_ci(root, rel):
    """大小写不敏感是否存在（模拟 Linux 上找不到的情形）。"""
    walked = walk_segments_ci(root, rel)
    return walked is not None and os.path.isfile(os.path.join(root, *walked))


def exists_cs(root, rel):
    """大小写**敏感**是否存在（逐段精确比对真实目录项名）。

    ⚠ 不能用 os.path.isfile：Windows 上它也不区分大小写，会永远返回 True。
    """
    parts = [p for p in rel.replace("\\", "/").split("/") if p and p != "."]
    cur = root
    for p in parts:
        if not os.path.isdir(cur):
            return False
        try:
            entries = os.listdir(cur)
        except OSError:
            return False
        if p not in entries:          # 精确匹配（区分大小写）
            return False
        cur = os.path.join(cur, p)
    return os.path.isfile(cur)


def looks_like_path(literal):
    return literal.endswith(PATH_EXTS)


def points_to_repo_file(literal):
    """字面量（反斜杠当分隔符）是否指向仓库里真实存在的文件。"""
    s = literal.replace("\\", "/")
    while s.startswith("./"):
        s = s[2:]
    if not s or s.startswith("/"):
        return False
    return os.path.isfile(os.path.join(REPO_ROOT, s.replace("/", os.sep)))


def java_files():
    for scan_dir in JAVA_SCAN_DIRS:
        if not os.path.isdir(scan_dir):
            continue
        for dirpath, _dirs, files in os.walk(scan_dir):
            for fn in files:
                if fn.endswith(".java"):
                    yield os.path.join(dirpath, fn)


def py_files():
    for scan_dir in PY_SCAN_DIRS:
        if not os.path.isdir(scan_dir):
            continue
        for dirpath, dirs, files in os.walk(scan_dir):
            dirs[:] = [d for d in dirs if d not in ("node_modules", "target", "dist", ".git")]
            for fn in files:
                if fn.endswith(".py"):
                    yield os.path.join(dirpath, fn)


def check_case():
    """P1：Java 的 resolve 常量必须大小写敏感地存在。"""
    hits = []
    seen = set()
    for path in java_files():
        try:
            text = open(path, encoding="utf-8").read()
        except (OSError, UnicodeDecodeError):
            continue
        rel_file = os.path.relpath(path, MODULE).replace("\\", "/")
        consts = {name: value for name, value in CONST_RE.findall(text)}
        if not consts:
            continue
        used = []
        for arg in RESOLVE_RE.findall(text):
            if arg.startswith('"'):
                used.append((arg[1:-1], "<字面量>"))
            else:
                literal = consts.get(arg)
                if literal is not None:
                    used.append((literal, arg))
        for literal, const_name in used:
            if not looks_like_path(literal):
                continue
            ci_roots = [(rname, rroot) for rname, rroot in CANDIDATE_ROOTS
                        if exists_ci(rroot, literal)]
            if not ci_roots:
                continue
            if any(exists_cs(rroot, literal) for _rname, rroot in ci_roots):
                continue
            key = (rel_file, const_name, literal)
            if key in seen:
                continue
            seen.add(key)
            rname, rroot = ci_roots[0]
            walked = walk_segments_ci(rroot, literal) or []
            hits.append((rel_file, const_name, literal, walked[-1] if walked else "?", rname))
    return hits


def check_separator():
    """P2：路径字面量的目录分隔符必须是正斜杠。"""
    hits = []
    self_path = os.path.abspath(__file__)
    sources = [(p, "java") for p in java_files()] + [(p, "py") for p in py_files()]
    for path, kind in sources:
        if os.path.abspath(path) == self_path:
            continue        # 不扫自己（本文件的说明文字里就写着反斜杠示例）
        try:
            text = open(path, encoding="utf-8").read()
        except (OSError, UnicodeDecodeError):
            continue
        rel_file = os.path.relpath(path, REPO_ROOT).replace("\\", "/")
        for m in LIT_RE.finditer(text):
            raw = m.group(1)
            if "\\" not in raw:
                continue
            decoded = decode_unicode_escapes(raw)
            if "\\" not in decoded:
                continue        # \\uXXXX 还原后没有反斜杠 ⇒ 不是分隔符问题
            if not looks_like_path(decoded):
                continue
            if not points_to_repo_file(decoded):
                continue        # 指向的文件不存在 ⇒ 多半是正则/转义，不是路径
            line = text[:m.start()].count("\n") + 1
            hits.append((rel_file, line, raw))
    return hits


def main():
    if not os.path.isdir(MODULE):
        print("[FAIL] 找不到 study 模块目录：%s" % MODULE)
        return 2

    case_hits = check_case()
    sep_hits = check_separator()
    n_java = sum(1 for _ in java_files())
    n_py = sum(1 for _ in py_files())

    if case_hits or sep_hits:
        if case_hits:
            print("发现 %d 处「路径大小写不对」—— Linux 上会读不到文件，Windows 上却是绿的：" % len(case_hits))
            for rel_file, const_name, literal, real, rname in case_hits:
                print("  ✗ %s" % rel_file)
                print("      常量 %s = \"%s\"" % (const_name, literal))
                print("      真实文件名 = %s（根：%s）" % (real, rname))
        if sep_hits:
            print("发现 %d 处「路径分隔符用了反斜杠」—— Linux 上反斜杠不是分隔符：" % len(sep_hits))
            for rel_file, line, raw in sep_hits:
                print("  ✗ %s:%d  \"%s\"" % (rel_file, line, raw))
        return 2

    print("ALL PASS 路径写法体检（扫描 %d 个 Java + %d 个 Python 文件，"
          "无大小写不匹配、无反斜杠分隔符）" % (n_java, n_py))
    return 0


if __name__ == "__main__":
    sys.exit(main())
