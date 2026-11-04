#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""jeecg-study 追溯矩阵生成器（机读）

从三份真实源稿解析，生成需求→接口→用例的追溯矩阵 CSV：

  01-需求迭代/02-需求文档/01-产品需求说明书.md        → 需求编号与名称
  02-代码开发/03-接口文档/01-接口设计说明书.md        → 接口总表（含「需求」列）
  03-测试验证/02-测试用例/01-测试用例.md              → 用例表（含「追溯」列）

输出：
  06-AI协作/01-任务台账/02-追溯矩阵.csv

端点实现校验（2026-11-04 新增）：
  追溯矩阵原先只看「接口总表里有没有这个路径」就判「已覆盖」，
  于是**文档承诺但代码没实现**的端点也会算覆盖 —— 2026-11-04 全量盘点发现 13 个此类端点
  （/mastery/weak-points、/practice/stats/today、/parent/notification-preference 等），
  追溯矩阵却显示「零缺口」。
  现在改为：**接口总表里的端点必须在后端控制器里真实存在（方法 + 路径双对上）才计入覆盖**，
  未实现的端点单独列出并使退出码 = 2。

用法：
  python 05-公共资源\\2026103015-追溯矩阵生成.py
退出码：0 = 生成成功且覆盖率达标；2 = 生成成功但存在未覆盖需求 / 未实现端点
"""

import csv
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

PRD = os.path.join(ROOT, "01-需求迭代", "02-需求文档", "01-产品需求说明书.md")
API = os.path.join(ROOT, "02-代码开发", "03-接口文档", "01-接口设计说明书.md")
CASE = os.path.join(ROOT, "03-测试验证", "02-测试用例", "01-测试用例.md")
OUT = os.path.join(ROOT, "06-AI协作", "01-任务台账", "02-追溯矩阵.csv")

# 后端控制器所在目录（基座内）
JAVA_ROOT = os.path.join(
    ROOT, "02-代码开发", "05-源码", "01-JeecgBoot基座", "jeecg-boot",
    "jeecg-boot-module", "jeecg-module-study", "src", "main", "java",
    "org", "jeecg", "modules", "study")

# API 根：接口总表里的路径不带这个前缀，代码里带 ⇒ 比对前统一补上
API_ROOT = "/api/v1/study"

REQ_RE = re.compile(r"\b(F-[A-Z]{3,5}-\d{2}|N-\d{2})\b")
REDLINE_RE = re.compile(r"\bR-0[1-4]\b")

# 后端端点扫描
CLASS_MAPPING_RE = re.compile(r'@RequestMapping\s*\(\s*"([^"]*)"')
METHOD_MAPPING_RE = re.compile(r'@(Get|Post|Put|Delete)Mapping(?:\s*\(\s*"([^"]*)"\))?')
PLACEHOLDER_RE = re.compile(r"\$\{[^}]*\}|\{[^}]*\}")


def _normalize(path):
    """占位符归一（{code} 与 ${code} → {}）+ 补前导斜杠 + 去尾斜杠。"""
    p = PLACEHOLDER_RE.sub("{}", path or "")
    if not p.startswith("/"):
        p = "/" + p
    if len(p) > 1 and p.endswith("/"):
        p = p[:-1]
    return p


def scan_backend_endpoints():
    """扫后端控制器 → 已实现端点集合（"GET /api/v1/study/xxx"）。

    扫不到任何端点时返回空集合 ⇒ 调用方会把它当「无法校验」而不是「全部未实现」。
    """
    found = set()
    if not os.path.isdir(JAVA_ROOT):
        return found
    for dirpath, _dirnames, filenames in os.walk(JAVA_ROOT):
        for fn in filenames:
            if not fn.endswith("Controller.java"):
                continue
            with open(os.path.join(dirpath, fn), "r", encoding="utf-8") as fh:
                src = fh.read()
            src = re.sub(r"(?s)/\*.*?\*/", " ", src)
            m = CLASS_MAPPING_RE.search(src)
            root = _normalize(m.group(1)) if m else ""
            for mm in METHOD_MAPPING_RE.finditer(src):
                method = mm.group(1).upper()
                path = _normalize(mm.group(2)) if mm.group(2) else ""
                if root and path:
                    full = re.sub(r"/{2,}", "/", root + path)
                else:
                    full = root or path
                found.add("%s %s" % (method, full))
    return found


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
    """接口总表 → [(需求编号列表, 方法, 路径, 说明)]。

    只认**端点总表**：第 1 列必须是「序号」（纯数字）。
    第 9 章「多用户改造对照表」也有「方法 / 路径」列，但没有序号列，
    若不排除，它里面的行会被当成端点（实测 `/parent/feedback` 就是这样混进来的
    —— ADR-030 已明确家长反馈归写作域 `/writing/parent/feedback`，
    `/parent/feedback` 是改造前的旧路径，只应出现在对照表里）。
    """
    out = []
    for header, body in scan_tables(text):
        mi = header_index(header, "方法")
        pi = header_index(header, "路径")
        ri = header_index(header, "需求")
        if mi < 0 or pi < 0:
            continue
        # 端点总表的第 1 列是序号；没有序号列 ⇒ 不是端点总表
        if not (len(header) > 0 and header[0].strip() in ("#", "序号", "No", "编号")):
            continue
        if mi == 0 or pi == 0:
            continue
        for cells in body:
            if len(cells) <= max(mi, pi):
                continue
            # 序号必须是纯数字
            if not cells[0].strip().isdigit():
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

    # ---- 端点实现校验（2026-11-04 新增）----
    # 接口总表里的端点必须在后端控制器里真实存在才计入覆盖。
    # 否则「文档写了但没实现」的端点会把追溯矩阵刷成「零缺口」（实测 13 个漏网）。
    backend = scan_backend_endpoints()
    check_enabled = bool(backend)
    unimplemented = []
    if check_enabled:
        for reqs, method, path, desc in apis:
            key = "%s %s" % (method, _normalize(API_ROOT + path))
            if key not in backend:
                unimplemented.append(key)
    unimplemented_set = set(unimplemented)

    api_by_req, case_by_req = {}, {}
    unimplemented_by_req = {}
    for reqs, method, path, desc in apis:
        key = "%s %s" % (method, path)
        norm = "%s %s" % (method, _normalize(API_ROOT + path))
        is_unimpl = check_enabled and norm in unimplemented_set
        for r in reqs:
            if is_unimpl:
                # 未实现的端点**不计入覆盖**，只登记到「未实现」清单
                unimplemented_by_req.setdefault(r, []).append(key)
            else:
                api_by_req.setdefault(r, []).append(key)
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
        # 接口存在但**全部未实现**、且无用例 ⇒ 不能算「已覆盖」
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
    if check_enabled:
        print("   端点实现校验: 后端控制器实存 %d 个端点；接口总表 %d 个端点中 %d 个未实现"
              % (len(backend), len(apis), len(unimplemented)))
    else:
        print("   [提示] 未找到后端控制器源码（%s）⇒ 跳过端点实现校验" % JAVA_ROOT)
    if undefined:
        print("   [缺口] PRD 引用但未定义: %s" % ", ".join(undefined))
    if uncovered:
        print("   [缺口] 已定义但无接口与用例引用: %s" % ", ".join(uncovered))
    if unimplemented:
        print("   [缺口] 接口总表承诺但后端未实现的端点（%d 个）:" % len(unimplemented))
        for k in sorted(unimplemented):
            print("     - %s" % k)
        return 2
    if uncovered:
        return 2
    print("   覆盖率: %d/%d 已定义需求均有接口或用例引用" % (len(names), len(names)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
