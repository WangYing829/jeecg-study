#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""jeecg-study · 闸门：表数口径检查（库侧三口径 + 文档侧业务表数）

为什么要有这道闸门
------------------
2026-11-05 收口「业务表数」时实测发现：文档里「业务表数」有**五个不同的数字**
（23 / 17 / 24 / 26 / 26）。但根因**不是有人写错了一个数字**，而是
**同一个词在仓库里有三种不同的算法**，谁抄谁的、抄到第几层都说不清：

| 口径 | 算法 | 实测值 |
|------|------|--------|
| **物理表** | `information_schema.TABLES` 里 `study\_%` 的每一张表（分区表按月各算一张） | **27** |
| **归一表** | 物理表去掉 `_YYYYMM` 分区后缀后去重 | **26** |
| **业务表** | 归一表再剔除 2 张基础设施表（`study_id_sequence` / `study_schema_migration`） | **24** |

「26」是**归一表数**（含 2 张基础设施表），「27」是**物理表数**
（多算了当月分区表 `study_practice_record_202611`），真正该写进文档的是**业务表 24 张**。

漂移是怎么固化下来的：`2026103021-唯一索引清单生成.py` 过去只查
`information_schema.STATISTICS`（**只能看见「恰好有唯一索引」的表**），
归一掉分区后缀就打印「26 张表」——那个 26 是**归一表数**，
却被 `AGENTS.md` 和任务台账当成了「业务表数」照抄 ⇒ 错的数字被当成事实。

所以这道闸门把三件事钉死：

D1 **库侧**：连真实库算三口径，与常量 **27 / 26 / 24** 比对，不符 → 退出码 2。
    ⚠ 表数**必须来自 `TABLES` 全量表**，不能来自「有唯一索引的表」——
    一张表漏建唯一索引就会从清单里消失，用它数出来的表数是残缺的。
D2 **文档侧**：**只**断言「业务表数」这一个数字。文件白名单 4 个（见 `DOC_FILES`），
    用精确正则抓「业务表 … N 张」/「N 张业务表」两种语序，N != 24 → 退出码 2，
    并指出**哪个文件哪一行**。
    ⚠ **只查业务表数**：用例数 / 测试类数 / 闸门数的句式太散，误报率高 ⇒ 一律不查。

退出码：0 = 全部通过；2 = 有问题（CI 直接红）；1 = 执行失败（连不上库等环境问题）。
"""

import os
import re
import sys

import pymysql
import yaml

# 仓库根按脚本位置推导（不写死 Windows 绝对路径）——
# 闸门要在 GitHub Actions 的 Linux runner 上跑，写死 `D:\...` 会直接找不到目录。
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join(ROOT, "02-代码开发", "05-源码", "01-JeecgBoot基座", "jeecg-boot")
MODULE = os.path.join(BASE, "jeecg-boot-module", "jeecg-module-study")
TEST_YML = os.path.join(MODULE, "src", "test", "resources", "application-test.yml")

# ---- 三口径基线（改动业务表后，三处要一起改：本脚本 / 2026103021 / 文档）------------
EXPECTED_PHYSICAL = 27
EXPECTED_NORMALIZED = 26
EXPECTED_BUSINESS = 24

# 基础设施表：不算业务表
INFRA_TABLES = ("study_id_sequence", "study_schema_migration")

# 分区表：study_practice_record_202610 → study_practice_record
PARTITION_SUFFIX = re.compile(r"_(\d{6})$")

# Spring 风格占位符：${VAR:默认值}
PLACEHOLDER = re.compile(r"^\$\{([A-Za-z_][A-Za-z0-9_]*)(?::(.*))?\}$")


# ------------------------------------------------------------------ 数据源（照抄 2026103021）

def resolve_secret(raw):
    """解析 Spring 占位符 `${VAR:默认值}`：优先取环境变量，其次回落默认值。

    口令改为环境变量注入后（见 05-公共资源/2026103023-凭据扫描.py），
    yml 里存的是占位符字面量而不是真实口令，直接拿去连库会认证失败。
    """
    text = (raw or "").strip()
    m = PLACEHOLDER.match(text)
    if not m:
        return text
    name, default = m.group(1), m.group(2)
    value = os.environ.get(name)
    if value:
        return value
    return (default or "").strip()


def load_datasource():
    """从模块测试配置里读数据源（不把口令写进脚本）。"""
    with open(TEST_YML, "r", encoding="utf-8") as f:
        cfg = yaml.safe_load(f)
    ds = cfg["spring"]["datasource"]["dynamic"]["datasource"]["master"]
    url = ds["url"]
    m = re.search(r"jdbc:mysql://([^:/]+):(\d+)/([^?]+)", url)
    if not m:
        raise SystemExit("无法解析数据源 URL：%s" % url)
    return (m.group(1), int(m.group(2)), m.group(3),
            resolve_secret(ds["username"]), resolve_secret(ds["password"]))


# ------------------------------------------------------------------ 库侧

def fetch_physical_tables(schema):
    """查 information_schema.TABLES，拿**全量** study_* 物理表。

    为什么不用 STATISTICS：STATISTICS 只列「恰好有唯一索引」的表，
    表漏建唯一索引就会从统计里消失 ⇒ 用它数出来的表数是残缺的。
    """
    sql = (
        "SELECT TABLE_NAME FROM information_schema.TABLES "
        "WHERE TABLE_SCHEMA = %s AND TABLE_NAME LIKE 'study\\_%%' "
        "ORDER BY TABLE_NAME"
    )
    conn = pymysql.connect(
        host=schema[0], port=schema[1], user=schema[3], password=schema[4],
        database=schema[2], charset="utf8mb4",
    )
    try:
        with conn.cursor() as cur:
            cur.execute(sql, (schema[2],))
            return [row[0] for row in cur.fetchall()]
    finally:
        conn.close()


def count_calibers(physical_tables):
    """算三口径：物理 / 归一 / 业务。"""
    normalized = sorted({PARTITION_SUFFIX.sub("", t) for t in physical_tables})
    business = [t for t in normalized if t not in INFRA_TABLES]
    return len(physical_tables), len(normalized), len(business)


def check_database():
    """D1：库侧三口径 vs 常量。返回 (问题列表, 三口径)。"""
    ds = load_datasource()
    physical = fetch_physical_tables(ds)
    counts = count_calibers(physical)
    problems = []
    for label, actual, expected in (
        ("物理表", counts[0], EXPECTED_PHYSICAL),
        ("归一表", counts[1], EXPECTED_NORMALIZED),
        ("业务表", counts[2], EXPECTED_BUSINESS),
    ):
        if actual != expected:
            problems.append(
                "  ✗ %s：实际 %d 张，期望 %d 张（常量写死在 2026110407 / 2026103021 里）"
                % (label, actual, expected)
            )
    return problems, counts


# ------------------------------------------------------------------ 文档侧

# 只查这 4 个文件里的「业务表数」表述
DOC_FILES = [
    "00-项目说明.md",
    "AGENTS.md",
    "02-代码开发/02-数据库/01-数据库设计说明书.md",
    "06-AI协作/01-任务台账/01-任务台账总表.md",
]

# 「业务表 … N 张」/「N 张业务表」两种语序。
#
# ⚠ **数字必须紧贴着「业务表」才算数**：
#   「平台表 133 张 + 业务表 24 张」里 133 属于平台表，不属于业务表 ——
#   若允许中间夹一整串文字，就会把别的表的数字算到业务表头上（假红）。
#   所以：
#     AFTER  ：业务表 与数字之间只允许标点/强调符号（「业务表 **24** 张」「业务表（本文件定义的 24 张）」）
#     BEFORE ：数字与业务表之间只允许空白/强调符号（「24 张业务表」）
#   且数字后面必须**紧跟**「张」（中间只允许空白/强调符号）——
#   否则「业务表**（物理 27 − 1 张分区重复…）」里的 27 会被误当成业务表数（那是**物理表**口径）。
COUNT_AFTER = re.compile(r"业务表[^0-9A-Za-z表]{0,12}(\d+)\s*\**\s*张")
COUNT_BEFORE = re.compile(r"(\d+)\s*张[\s*]*业务表")
# META facts 形态：{"v": "24", "k": "业务表"}
COUNT_FACTS = re.compile(r'"v"\s*:\s*"(\d+)"\s*,\s*"k"\s*:\s*"业务表"')

# 句读切分：表格单元格 / 分句，避免跨句误配
#（例：「平台表 133 张 + 业务表 24 张」在一格里，两句数各归各的）
CLAUSE_SPLIT = re.compile(r"[|｜；;，,、]")

# 脚本级口径：分句里提到具体脚本 ⇒ 说的是「这个脚本建了几张表」，不是全库业务表口径。
#
# ⚠ 判定范围是**整个分句**，不是「数字之前的那一段」：
#   「业务表另 7 张由增量脚本 12/14/23 建」里脚本名在数字**之后**，
#   只看前缀会漏判 ⇒ 把正确的脚本级表述当成全库口径 ⇒ 假红。
# 代价：同一分句里若同时写了「全库业务表 N 张」和某个脚本名，那句不查。
#   ⇒ 全库口径的表述要单独成句，别和脚本名写在同一分句里。
SCRIPT_REF = re.compile(r"\d{2}-[^\s]*\.sql|增量脚本")

# 枚举续接：数字后面紧跟「 + 」⇒ 是枚举里的一项（「业务表 17 张 + child_id 增量」），不是总数
ENUM_TAIL = re.compile(r"^\s*\**\s*\+")


def evaluate_clause(clause, match):
    """判定一个候选表述是不是「全库业务表数」；是则返回数字，否则返回 None。"""
    if SCRIPT_REF.search(clause):
        return None            # 脚本级口径（02-schema.sql 建了几张表之类）
    if ENUM_TAIL.match(clause[match.end():]):
        return None            # 枚举里的一项，不是总数
    return int(match.group(1))


def check_documents():
    """D2：文档侧「业务表数」表述 vs 常量。返回 (问题列表, 每个文件的命中数)。"""
    problems = []
    hits = {}
    for rel in DOC_FILES:
        path = os.path.join(ROOT, rel.replace("/", os.sep))
        if not os.path.isfile(path):
            problems.append("  ✗ %s：文件不存在（口径文件被删了？）" % rel)
            hits[rel] = 0
            continue
        with open(path, "r", encoding="utf-8") as f:
            lines = f.read().splitlines()
        count = 0
        for lineno, line in enumerate(lines, start=1):
            # ⚠ facts 形态必须**在整行上**匹配：{"v": "23", "k": "业务表"}
            #   中间有一个英文逗号，先按句读切分会把数字和「业务表」拆到两段，
            #   切完就再也配不上了 ⇒ facts 一律整行判。
            for match in COUNT_FACTS.finditer(line):
                count += 1
                value = int(match.group(1))
                if value != EXPECTED_BUSINESS:
                    problems.append(
                        "  ✗ %s:%d 业务表数写的是 %d 张，应为 %d 张 —— %s"
                        % (rel, lineno, value, EXPECTED_BUSINESS, line.strip()[:60])
                    )
            for clause in CLAUSE_SPLIT.split(line):
                if "业务表" not in clause:
                    continue
                for pattern in (COUNT_AFTER, COUNT_BEFORE):
                    for match in pattern.finditer(clause):
                        value = evaluate_clause(clause, match)
                        if value is None:
                            continue
                        count += 1
                        if value != EXPECTED_BUSINESS:
                            problems.append(
                                "  ✗ %s:%d 业务表数写的是 %d 张，应为 %d 张 —— %s"
                                % (rel, lineno, value, EXPECTED_BUSINESS,
                                   line.strip()[:60])
                            )
        hits[rel] = count
    return problems, hits


# ------------------------------------------------------------------ 入口

def main():
    try:
        db_problems, counts = check_database()
    except SystemExit:
        raise
    except Exception as exc:  # noqa: BLE001 - 脚本入口要给出可读原因
        print("FAIL 连库失败，无法核对表数口径：%s" % exc)
        return 1

    doc_problems, hits = check_documents()

    if db_problems or doc_problems:
        print("FAIL 表数口径不一致：")
        for item in db_problems:
            print(item)
        for item in doc_problems:
            print(item)
        print("")
        print("  口径定义：物理 %d / 归一 %d / 业务 %d（归一 = 物理去掉 _YYYYMM 分区后缀；"
              "业务 = 归一再剔除 %s）"
              % (counts[0], counts[1], counts[2], "、".join(INFRA_TABLES)))
        print("  改表后请同步更新：05-公共资源/2026103021-唯一索引清单生成.py、"
              "本脚本的三口径常量，以及 %s 里的业务表数表述。"
              % "、".join(DOC_FILES))
        return 2

    print("OK 表数口径一致（物理 %d / 归一 %d / 业务 %d），文档 %d 处表述一致"
          % (counts[0], counts[1], counts[2], len(DOC_FILES)))
    detail = " · ".join("%s %d 处" % (rel, hits.get(rel, 0)) for rel in DOC_FILES)
    print("   文档命中：%s" % detail)
    return 0


if __name__ == "__main__":
    sys.exit(main())
