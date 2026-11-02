#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""jeecg-study 追溯矩阵生成器（机读）

从三份真实源稿解析，生成需求→接口→用例的追溯矩阵 CSV：

  01-需求迭代/02-需求文档/2026103002-产品需求说明书.md        → 需求编号与名称
  02-代码开发/03-接口文档/2026103006-接口设计说明书.md        → 接口总表（含「需求」列）
  03-测试验证/02-测试用例/2026103009-测试用例.md              → 用例表（含「追溯」列）

输出：
  06-AI协作/01-任务台账/02-追溯矩阵.csv

用法：
  python 05-公共资源\\2026103015-追溯矩阵生成.py
退出码：0 = 生成成功且覆盖率达标；2 = 生成成功但存在未覆盖需求
"""

import csv
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

PRD = os.path.join(ROOT, "01-需求迭代", "02-需求文档", "2026103002-产品需求说明书.md")
API = os.path.join(ROOT, "02-代码开发", "03-接口文档", "2026103006-接口设计说明书.md")
CASE = os.path.join(ROOT, "03-测试验证", "02-测试用例", "2026103009-测试用例.md")
OUT = os.path.join(ROOT, "06-AI协作", "01-任务台账", "02-追溯矩阵.csv")

REQ_RE = re.compile(r"\b(F-[A-Z]{3,5}-\d{2}|N-\d{2})\b")
REDLINE_RE = re.compile(r"\bR-0[1-4]\b")


def read(path):
    with open(path, "r", encoding="utf-8") as fh:
        return fh.read()


def clean(cell):
    """去掉 markdown 强调、反引号、换行标记、HTML 标签。"""
    cell = re.sub(r"<br\s*/?>", " ", cell)
    cell = re.sub(r"[*`]", "", cell)
    return " ".join(cell.split())


def split_row(line):
    line = line.strip()
    if not line.startswith("|"):
        return []
    parts = line.split("|")
    if len(parts) < 3:
        return []
    return [clean(p) for p in parts[1:-1]]


def scan_tables(text):
    """扫描全部 Markdown 表格 → [(表头列表, 数据行列表)]。"""
    tables, cur = [], []
    for line in text.splitlines() + [""]:
        s = line.strip()
        if s.startswith("|"):
            cur.append(s)
            continue
        if cur:
            tables.append(cur)
            cur = []
    out = []
    for rows in tables:
        header = split_row(rows[0])
        if not header:
            continue
        body = []
        for r in rows[1:]:
            cells = split_row(r)
            if not cells:
                continue
            if all(re.fullmatch(r":?-{2,}:?", c) for c in cells if c):
                continue
            body.append(cells)
        if body:
            out.append((header, body))
    return out


def header_index(header, *names):
    for n in names:
        for i, h in enumerate(header):
            if h == n or n in h:
                return i
    return -1


def parse_prd(text):
    """需求名称来自两处：'### N.M F-XXX-NN 名称' 标题行，以及 '编号|需求' 表格。"""
    names, modules = {}, {}
    module = ""
    for line in text.splitlines():
        if line.startswith("## "):
            module = clean(line[3:])
        m = re.match(r"^###\s+[\d.]+\s+((?:F-[A-Z]{3,5}|N)-\d{2})\s+(.+?)\s*$", line)
        if m:
            rid, title = m.group(1), clean(m.group(2))
            names.setdefault(rid, title)
            modules.setdefault(rid, module)

    for header, body in scan_tables(text):
        ci = header_index(header, "编号")
        ti = header_index(header, "需求", "要求")
        if ci < 0 or ti < 0:
            continue
        for cells in body:
            if len(cells) <= max(ci, ti):
                continue
            rid = cells[ci]
            if not REQ_RE.fullmatch(rid):
                continue
            names.setdefault(rid, cells[ti])
    return names, modules


def parse_api(text):
    """接口总表 → [(需求编号列表, 方法, 路径, 说明)]。"""
    out = []
    for header, body in scan_tables(text):
        mi = header_index(header, "方法")
        pi = header_index(header, "路径")
        ri = header_index(header, "需求")
        if mi < 0 or pi < 0:
            continue
        for cells in body:
            if len(cells) <= max(mi, pi):
                continue
            method, path = cells[mi], cells[pi]
            if method not in ("GET", "POST", "PUT", "DELETE"):
                continue
            desc = cells[pi + 1] if len(cells) > pi + 1 else ""
            req_cell = cells[ri] if (ri >= 0 and len(cells) > ri) else ""
            reqs = REQ_RE.findall(req_cell) + REDLINE_RE.findall(req_cell)
            out.append((reqs, method, path, desc))
    return out


def parse_cases(text):
    """用例表 → [(用例编号, 名称, 追溯需求列表, 级别)]。"""
    out = []
    for line in text.splitlines():
        cells = split_row(line)
        if len(cells) != 7:
            continue
        cid, name = cells[0], cells[1]
        if not re.match(r"^TC-\d{2}-\d{2,3}$", cid):
            continue
        reqs = REQ_RE.findall(cells[6]) + REDLINE_RE.findall(cells[6])
        level = cells[5]
        out.append((cid, name, sorted(set(reqs)), level))
    return out


def main():
    for p in (PRD, API, CASE):
        if not os.path.exists(p):
            print("MISSING %s" % p)
            return 1

    names, modules = parse_prd(read(PRD))
    apis = parse_api(read(API))
    cases = parse_cases(read(CASE))

    api_by_req, case_by_req = {}, {}
    for reqs, method, path, desc in apis:
        for r in reqs:
            api_by_req.setdefault(r, []).append("%s %s" % (method, path))
    for cid, cname, reqs, level in cases:
        for r in reqs:
            case_by_req.setdefault(r, []).append("%s(%s)" % (cid, level))

    rows = []
    uncovered = []
    undefined = []
    prd_text = read(PRD)
    referenced = set(REQ_RE.findall(prd_text))

    for rid in sorted(names, key=lambda x: (x.startswith("N"), x)):
        a = api_by_req.get(rid, [])
        c = case_by_req.get(rid, [])
        status = "已覆盖" if (a or c) else "未覆盖"
        if not (a or c):
            uncovered.append(rid)
        rows.append([
            rid,
            names[rid],
            modules.get(rid, ""),
            len(a),
            " | ".join(a),
            len(c),
            " | ".join(c),
            status,
        ])

    # PRD 中被引用、但没有正式定义条目的编号（真实缺口，不掩盖）
    for rid in sorted(referenced - set(names)):
        undefined.append(rid)
        rows.append([
            rid,
            "【被引用但 PRD 未定义】",
            "",
            len(api_by_req.get(rid, [])),
            " | ".join(api_by_req.get(rid, [])),
            len(case_by_req.get(rid, [])),
            " | ".join(case_by_req.get(rid, [])),
            "被引用未定义",
        ])

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8-sig", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["需求编号", "需求名称", "所属模块", "接口数", "接口清单",
                    "用例数", "用例清单", "追溯状态"])
        w.writerows(rows)

    print("OK %s (%d rows, %d apis, %d cases)"
          % (os.path.relpath(OUT, ROOT), len(rows), len(apis), len(cases)))
    print("   需求编号: PRD 出现 %d 个 / 正式定义 %d 个" % (len(referenced), len(names)))
    if undefined:
        print("   [缺口] PRD 引用但未定义: %s" % ", ".join(undefined))
    if uncovered:
        print("   [缺口] 已定义但无接口与用例引用: %s" % ", ".join(uncovered))
        return 2
    if undefined:
        return 2
    print("   覆盖率: %d/%d 已定义需求均有接口或用例引用" % (len(names), len(names)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
