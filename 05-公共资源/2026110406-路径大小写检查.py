#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
jeecg-study · 闸门：路径大小写体检（Linux 区分大小写）

为什么要有这道闸门
------------------
2026-11-04 CI 第 7 次 run 暴露了一类**只有 Linux 才炸、本地永远绿**的错：

    StudyRecordTableGuardTest
        常量  TABLE_MAPPER_FILE = "common/record/mapper/studyrecordtablemapper.java"
        用法  RedLineScanner.studyMainRoot().resolve(TABLE_MAPPER_FILE)
        真实  common/record/mapper/StudyRecordTableMapper.java

* Windows 的文件系统**不区分大小写** ⇒ 这条路径照样能找到文件 ⇒ 本地 355 条断言全绿；
* Linux **区分大小写** ⇒ 找不到文件。而当时的 `RedLineScanner.read()` 读不到文件
  会**静默返回空串** ⇒ 列集合解析成空 ⇒ 三条「三处同步」断言连锁失败，
  而且失败信息是「列集合解析不出来」，完全指不到「路径大小写不对」这个真因。

这类错的两个特征让它极难自查：
1. **本地绿、CI 红**（平台差异，不是代码逻辑问题）；
2. **失败信息指向下游**（列集合不一致），不指向路径本身。

所以把「路径大小写」变成提交前的可执行断言。

检查项
------
对 study 模块的 Java 源码里**被用于 `resolve(...)` 的路径常量**，
逐个判断它在候选根目录下：

* **大小写不敏感**存在（CI）但**大小写敏感**不存在（CS）⇒ 命中 ⇒ 报出来。

只检查「真的拿去做 resolve」的常量 —— 那些只用于**字符串比对**的常量
（如 `moduleRelative()` 的小写结果）大小写不同是**故意的**，不该报。

退出码：0 = 全部通过；2 = 有问题（CI 直接红）
"""

import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

BASE = os.path.join(REPO_ROOT, "02-代码开发", "05-源码", "01-JeecgBoot基座")
MODULE = os.path.join(BASE, "jeecg-boot", "jeecg-boot-module", "jeecg-module-study")

# ---- 候选根目录：路径常量可能被 resolve 到这些根下 ------------------------------
CANDIDATE_ROOTS = [
    ("仓库根", REPO_ROOT),
    ("SQL 脚本目录", os.path.join(REPO_ROOT, "02-代码开发", "02-数据库", "03-脚本")),
    ("study 主源码根", os.path.join(MODULE, "src", "main", "java",
                                    "org", "jeecg", "modules", "study")),
    ("study 测试资源", os.path.join(MODULE, "src", "test", "resources")),
    ("前端源码根", os.path.join(BASE, "jeecgboot-vue3", "src")),
]

# 只扫 study 模块（main + test）的 Java 源码
SCAN_DIRS = [
    os.path.join(MODULE, "src", "main", "java"),
    os.path.join(MODULE, "src", "test", "java"),
]

# 路径常量的取值：String NAME = "....";
CONST_RE = re.compile(
    r'\b(?:static\s+final\s+)?String\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*"([^"\\\n]{2,300})"')

# resolve(...) 的实参：标识符或字符串字面量
RESOLVE_RE = re.compile(r'\.resolve\(\s*([A-Za-z_][A-Za-z0-9_]*|"[^"\\\n]*")\s*\)')

# 像路径的取值（带已知扩展名）
PATH_EXTS = (
    ".java", ".vue", ".ts", ".tsx", ".js", ".jsx", ".sql", ".md", ".html",
    ".json", ".yml", ".yaml", ".less", ".scss", ".xml", ".properties",
)


def _walk_segments(root, rel):
    """按路径分段逐级下钻，返回 [(目录项真实名, 路径)] 或 None（中途不存在）。"""
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
    """大小写**不敏感**是否存在（模拟 Linux 上找不到的情形）。"""
    walked = _walk_segments(root, rel)
    return walked is not None and os.path.isfile(os.path.join(root, *[w for w in walked]))


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


def scan():
    """返回命中列表：[（源文件相对路径, 常量名, 字面量, 真实文件名, 根名）]"""
    hits = []
    seen = set()
    for scan_dir in SCAN_DIRS:
        if not os.path.isdir(scan_dir):
            continue
        for dirpath, _dirs, files in os.walk(scan_dir):
            for fn in files:
                if not fn.endswith(".java"):
                    continue
                path = os.path.join(dirpath, fn)
                try:
                    text = open(path, encoding="utf-8").read()
                except (OSError, UnicodeDecodeError):
                    continue
                rel_file = os.path.relpath(path, MODULE).replace("\\", "/")

                consts = {name: value for name, value in CONST_RE.findall(text)}
                if not consts:
                    continue

                # 只看「真的拿去做 resolve」的常量
                used = []
                for arg in RESOLVE_RE.findall(text):
                    if arg.startswith('"'):
                        literal = arg[1:-1]
                        used.append((literal, "<字面量>"))
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
                    # 大小写不敏感能命中、敏感命中不了 ⇒ 大小写不对
                    key = (rel_file, const_name, literal)
                    if key in seen:
                        continue
                    seen.add(key)
                    rname, rroot = ci_roots[0]
                    walked = _walk_segments(rroot, literal) or []
                    real = walked[-1] if walked else "?"
                    hits.append((rel_file, const_name, literal, real, rname))
    return hits


def main():
    if not os.path.isdir(MODULE):
        print("[FAIL] 找不到 study 模块目录：%s" % MODULE)
        return 2

    hits = scan()
    scanned = 0
    for scan_dir in SCAN_DIRS:
        if os.path.isdir(scan_dir):
            for _d, _s, fs in os.walk(scan_dir):
                scanned += sum(1 for f in fs if f.endswith(".java"))

    if hits:
        print("发现 %d 处「路径大小写不对」—— Linux 上会读不到文件，Windows 上却是绿的：" % len(hits))
        for rel_file, const_name, literal, real, rname in hits:
            print("  ✗ %s" % rel_file)
            print("      常量 %s = \"%s\"" % (const_name, literal))
            print("      真实文件名 = %s（根：%s）" % (real, rname))
        return 2

    print("ALL PASS 路径大小写体检（扫描 %d 个 Java 文件，无大小写不匹配的路径常量）" % scanned)
    return 0


if __name__ == "__main__":
    sys.exit(main())
