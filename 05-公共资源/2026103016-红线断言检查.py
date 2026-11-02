#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""jeecg-study 红线与追溯门禁

在写代码之前先跑这个脚本。它做四件事：

  1. 24 条红线断言编号是否齐全（R01-1..5 / R02-1..7 / R03-1..6 / R04-1..6）
  2. 四条产品红线 R-01..R-04 是否在 PRD / 系统设计 / 接口设计 / 测试用例 四处都出现
  3. 追溯矩阵是否存在、覆盖了多少需求
  4. 需求编号的缺口（被引用未定义 / 已定义但接口与用例零引用）——这些是返工高发区

用法：
  python 05-公共资源\\2026103016-红线断言检查.py
退出码：0 = 全绿；2 = 有缺口（须逐条人工判读，不得直接忽略）
"""

import csv
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

DOCS = {
    "PRD": "01-需求迭代/02-需求文档/01-产品需求说明书.md",
    "系统设计": "02-代码开发/01-设计文档/01-系统设计说明书.md",
    "接口设计": "02-代码开发/03-接口文档/01-接口设计说明书.md",
    "测试用例": "03-测试验证/02-测试用例/01-测试用例.md",
}

EXPECTED_REDLINE = (["R01-%d" % i for i in range(1, 6)]
                    + ["R02-%d" % i for i in range(1, 8)]
                    + ["R03-%d" % i for i in range(1, 7)]
                    + ["R04-%d" % i for i in range(1, 7)])

REDLINE_IDS = ["R-01", "R-02", "R-03", "R-04"]
REQ_RE = re.compile(r"\b(F-[A-Z]{3,5}-\d{2}|N-\d{2})\b")

failures = []
notes = []


def read(rel):
    p = os.path.join(ROOT, rel.replace("/", os.sep))
    if not os.path.exists(p):
        return None
    with open(p, "r", encoding="utf-8") as fh:
        return fh.read()


def main():
    print("=" * 68)
    print("jeecg-study 红线与追溯门禁")
    print("=" * 68)

    texts = {}
    for name, rel in DOCS.items():
        t = read(rel)
        if t is None:
            print("[FAIL] 文档缺失: %s (%s)" % (name, rel))
            failures.append("文档缺失 " + name)
            texts[name] = ""
        else:
            texts[name] = t
    if failures:
        print("\nFAIL: 源稿不完整，无法继续检查")
        return 2

    # ---- 1. 24 条红线断言编号齐全 ----
    cases = texts["测试用例"]
    missing = [r for r in EXPECTED_REDLINE if not re.search(r"\|\s*%s\s*\|" % re.escape(r), cases)]
    if missing:
        print("[FAIL] 红线断言缺失 %d 条: %s" % (len(missing), ", ".join(missing)))
        failures.append("红线断言缺失")
    else:
        print("[PASS] 24 条红线断言编号齐全 (R01-1..5 / R02-1..7 / R03-1..6 / R04-1..6)")

    # ---- 2. 四条红线在四份文档中都出现 ----
    for rid in REDLINE_IDS:
        absent = [n for n in DOCS if rid not in texts[n]]
        if absent:
            print("[FAIL] %s 未出现在: %s" % (rid, ", ".join(absent)))
            failures.append("%s 覆盖不全" % rid)
        else:
            print("[PASS] %s 在 PRD / 系统设计 / 接口设计 / 测试用例 四处均出现" % rid)

    # ---- 3. 追溯矩阵 ----
    matrix = os.path.join(ROOT, "06-AI协作", "01-任务台账", "02-追溯矩阵.csv")
    if not os.path.exists(matrix):
        print("[FAIL] 追溯矩阵不存在，请先运行 2026103015-追溯矩阵生成.py")
        failures.append("追溯矩阵缺失")
        rows = []
    else:
        with open(matrix, "r", encoding="utf-8-sig", newline="") as fh:
            rows = list(csv.DictReader(fh))
        covered = sum(1 for r in rows if r.get("追溯状态") == "已覆盖")
        undef = [r["需求编号"] for r in rows if r.get("追溯状态") == "被引用未定义"]
        untrace = [r["需求编号"] for r in rows if r.get("追溯状态") == "未覆盖"]
        print("[INFO] 追溯矩阵 %d 行：已覆盖 %d / 被引用未定义 %d / 未覆盖 %d"
              % (len(rows), covered, len(undef), len(untrace)))
        if undef:
            print("[FAIL] PRD 引用但未正式定义: %s" % ", ".join(undef))
            failures.append("需求编号被引用未定义")
        if untrace:
            print("[FAIL] 已定义但接口与用例零引用（实现时会静默漏掉）: %s" % ", ".join(untrace))
            failures.append("需求未被接口/用例追溯")

    # ---- 4. PRD 需求编号定义完整性 ----
    prd = texts["PRD"]
    referenced = set(REQ_RE.findall(prd))
    print("[INFO] PRD 出现需求编号 %d 个" % len(referenced))
    if len(referenced) < 69:
        print("[WARN] 需求编号总数少于 69（60 功能 + 9 非功能），请核对 PRD")
        notes.append("PRD 需求编号数异常")

    # ---- 5. 架构决策与文档的一致性提示 ----
    for name in DOCS:
        if "jeecg-module-study" not in texts[name] and name == "系统设计":
            print("[WARN] 系统设计中未见 jeecg-module-study 模块名")
            notes.append("系统设计缺模块名")

    print("-" * 68)
    if failures:
        print("FAIL (%d 项): %s" % (len(failures), "; ".join(failures)))
        print("处置：逐条人工判读。是真实缺口就先补文档再写代码；")
        print("      是编号体系本身的设计选择（如底座能力由业务需求承载），")
        print("      在 06-AI协作/01-任务台账/01-任务台账总表.md 登记豁免理由。")
        return 2
    if notes:
        print("PASS with notes: %s" % "; ".join(notes))
        return 0
    print("ALL PASS — 红线齐全、追溯闭合，可进入编码阶段")
    return 0


if __name__ == "__main__":
    sys.exit(main())
