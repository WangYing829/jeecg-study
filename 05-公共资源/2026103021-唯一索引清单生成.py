# -*- coding: utf-8 -*-
"""唯一索引清单生成器（ADR-031 · P4 工程卫生）。

为什么需要这个脚本
------------------
`StudySqlOrderDeterminismTest` 的 SQL-C 规则要求：
「LIMIT 1 的 ORDER BY 末位键必须落在该表的唯一索引上」。
判定「唯一」必须有**真实库结构**的依据，而这份依据过去是**手写快照**
（写死在测试类里的 Map）。手写快照有三个致命问题：

1. **会过期**：新增业务表 / 改索引后，快照不会跟着变 → 规则要么误报，
   要么把本该报的问题放过；
2. **会漂移**：没人说得清快照是哪天导出的、对应哪套库；
3. **不可复现**：换一台机器 / 换一个库，快照就对不上了。

所以把「唯一索引清单」改成**从 information_schema 自动生成**的产物：
本脚本连库导出 → 写成 JSON 资源 → 测试从 classpath 读这份资源。
清单与真实库结构的一致性由本脚本保证，测试只负责「用这份清单判规则」。

三个口径：为什么「表数」不能只查 STATISTICS
-------------------------------------------
本脚本查**两张**系统视图，用途完全不同，**不能互相替代**：

| 视图 | 回答的问题 | 本项目实测 |
|------|------------|------------|
| `information_schema.TABLES` | 库里**一共有几张** `study_%` 表 | **物理 27** 张 |
| `information_schema.STATISTICS` | 每张表**有哪些唯一索引** | 清单内容（归一后 26 张表有唯一索引） |

⚠ **表数必须来自 TABLES**。STATISTICS 只列「恰好有唯一索引」的表 ——
一张表漏建唯一索引，它就从 STATISTICS 里消失了 ⇒ 用它数出来的「表数」是残缺的，
会让人误以为库里的表比实际少。而**唯一索引清单**（JSON 的内容）只能来自 STATISTICS。

三种「表数」口径（本项目 2026-11-05 连真库实测，别再混用）
--------------------------------------------------------
| 口径 | 怎么算 | 实测 | 含义 |
|------|--------|------|------|
| **物理表** | `TABLES` 里 `study\_%` 的每一张表 | **27** | 含 2 张分区表（`_202610` / `_202611` 是同一逻辑表的两个月） |
| **归一表** | 物理表去掉 `_YYYYMM` 分区后缀后去重 | **26** | 含 2 张基础设施表（`study_id_sequence` / `study_schema_migration`） |
| **业务表** | 归一表再剔除那 2 张基础设施表 | **24** | ★ **文档里该写的「业务表数」就是这个** |

历史包袱（2026-11-05 修）：本脚本过去**只查 STATISTICS**、只归一了分区后缀，
就打印「26 张表」—— 那个 26 其实是**归一表数**，却被 `AGENTS.md` 与任务台账
当成了「业务表数」照抄 ⇒ 错的数字被当成事实传了三处。
现在本脚本把三口径分开打印，并**内置断言：业务表数必须 == 24**，
否则退出码 2 并说明实际值；配套闸门见 `05-公共资源/2026110407-表数口径检查.py`。

用法
----
    # 重新生成（写文件）
    python -X utf8 .\\05-公共资源\\2026103021-唯一索引清单生成.py

    # 只校验不写文件：与库里的结构不一致就退出码 2（可当闸门用）
    python -X utf8 .\\05-公共资源\\2026103021-唯一索引清单生成.py --check

退出码
------
0 = 成功（生成成功，或 --check 通过）；2 = --check 发现漂移 / 业务表数不等于 24；
1 = 执行失败。
"""
import argparse
import datetime
import json
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
OUT_JSON = os.path.join(MODULE, "src", "test", "resources", "study-unique-indexes.json")

# 分区表：study_practice_record_202610 → study_practice_record
# （判定唯一性时按「表名前缀」匹配，分区表与基表共用同一套唯一索引）
PARTITION_SUFFIX = re.compile(r"_(\d{6})$")

# 基础设施表：不计入「业务表」口径（发号器 + 迁移登记表，都不是业务数据）
INFRA_TABLES = ("study_id_sequence", "study_schema_migration")

# 业务表口径基线：改业务表后必须**显式**更新这里的常量，
# 同时更新 05-公共资源/2026110407-表数口径检查.py 与文档里的表述。
# 它的作用是把「加表」从「顺手改个数」变成「必须走一次口径评审」。
EXPECTED_BUSINESS_TABLES = 24

# Spring 风格占位符：${VAR:默认值}
PLACEHOLDER = re.compile(r"^\$\{([A-Za-z_][A-Za-z0-9_]*)(?::(.*))?\}$")


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


def connect(schema):
    """连库（口令来自环境变量，见 load_datasource）。"""
    return pymysql.connect(
        host=schema[0], port=schema[1], user=schema[3], password=schema[4],
        database=schema[2], charset="utf8mb4",
    )


def fetch_physical_tables(schema):
    """查 information_schema.TABLES，拿**全量** study_* 物理表。

    为什么必须单独查 TABLES：STATISTICS 只列「恰好有唯一索引」的表，
    一张表漏建唯一索引就会从 STATISTICS 里消失 ⇒ 用它数出来的「表数」是残缺的。
    **表数只能来自 TABLES 全量表。**
    """
    sql = (
        "SELECT TABLE_NAME FROM information_schema.TABLES "
        "WHERE TABLE_SCHEMA = %s AND TABLE_NAME LIKE 'study\\_%%' "
        "ORDER BY TABLE_NAME"
    )
    conn = connect(schema)
    try:
        with conn.cursor() as cur:
            cur.execute(sql, (schema[2],))
            return [row[0] for row in cur.fetchall()]
    finally:
        conn.close()


def count_calibers(physical_tables):
    """算三个口径：物理 / 归一 / 业务。

    - 物理：TABLES 里的每一张表（分区表 _202610 / _202611 各算一张）
    - 归一：物理表去掉 _YYYYMM 分区后缀后去重
    - 业务：归一表再剔除基础设施表（study_id_sequence / study_schema_migration）
    """
    normalized = sorted({PARTITION_SUFFIX.sub("", t) for t in physical_tables})
    business = [t for t in normalized if t not in INFRA_TABLES]
    return {
        "physical": len(physical_tables),
        "normalized": len(normalized),
        "business": len(business),
    }


def fetch_unique_indexes(schema):
    """查 information_schema，导出所有 study_* 表的唯一索引。"""
    sql = (
        "SELECT TABLE_NAME, INDEX_NAME, "
        "GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) "
        "FROM information_schema.STATISTICS "
        "WHERE TABLE_SCHEMA = %s AND TABLE_NAME LIKE 'study\\_%%' AND NON_UNIQUE = 0 "
        "GROUP BY TABLE_NAME, INDEX_NAME "
        "ORDER BY TABLE_NAME, INDEX_NAME"
    )
    conn = connect(schema)
    try:
        with conn.cursor() as cur:
            cur.execute(sql, (schema[2],))
            rows = cur.fetchall()
    finally:
        conn.close()

    tables = {}
    for table_name, index_name, columns in rows:
        cols = [c.strip() for c in columns.split(",") if c.strip()]
        if not cols:
            continue
        key = PARTITION_SUFFIX.sub("", table_name)
        bucket = tables.setdefault(key, [])
        if cols not in bucket:
            bucket.append(cols)
    return tables


def build_payload(tables):
    """组装输出内容（不含时间戳，便于 --check 做稳定比对）。"""
    return {
        "schema": "jeecg-study",
        "source": "information_schema.STATISTICS (NON_UNIQUE = 0)",
        "generator": "05-公共资源/2026103021-唯一索引清单生成.py",
        "tables": {name: tables[name] for name in sorted(tables)},
    }


def serialize(payload):
    """稳定序列化：缩进 2、键序固定、结尾换行。"""
    return json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=False) + "\n"


def main():
    parser = argparse.ArgumentParser(description="生成/校验唯一索引清单")
    parser.add_argument("--check", action="store_true", help="只校验不写文件")
    args = parser.parse_args()

    try:
        ds = load_datasource()
        physical_tables = fetch_physical_tables(ds)
        tables = fetch_unique_indexes(ds)
    except Exception as exc:  # noqa: BLE001 - 脚本入口要给出可读原因
        print("FAIL 无法导出唯一索引清单：%s" % exc)
        return 1

    if not tables:
        print("FAIL 导出的唯一索引清单为空（表名前缀过滤是否写错了？）")
        return 1

    calibers = count_calibers(physical_tables)
    caliber_line = "物理 %d / 归一 %d / 业务 %d" % (
        calibers["physical"], calibers["normalized"], calibers["business"])

    # 内置断言：业务表数必须等于基线。
    # 加表 / 删表不是「顺手改个数」的事 —— 必须同时更新：
    #   ① 本脚本的 EXPECTED_BUSINESS_TABLES
    #   ② 05-公共资源/2026110407-表数口径检查.py 的三口径常量
    #   ③ 文档里的业务表数表述（AGENTS.md / 00-项目说明.md / 数据库设计说明书 / 台账）
    if calibers["business"] != EXPECTED_BUSINESS_TABLES:
        print("FAIL 业务表数与基线不符：实际 %d 张，期望 %d 张（%s）"
              % (calibers["business"], EXPECTED_BUSINESS_TABLES, caliber_line))
        print("     加表 / 删表后请同步更新：2026103021 与 2026110407 的口径常量，以及文档里的业务表数表述")
        return 2

    payload = build_payload(tables)
    content = serialize(payload)

    if args.check:
        if not os.path.isfile(OUT_JSON):
            print("FAIL 清单文件不存在：%s" % OUT_JSON)
            print("     请先运行：python -X utf8 .\\05-公共资源\\2026103021-唯一索引清单生成.py")
            return 2
        with open(OUT_JSON, "r", encoding="utf-8") as f:
            current = f.read()
        if current != content:
            print("FAIL 清单与真实库结构不一致（唯一索引有增删改）")
            print("     请重新运行：python -X utf8 .\\05-公共资源\\2026103021-唯一索引清单生成.py")
            return 2
        print("OK 唯一索引清单与真实库一致（%s；清单含 %d 张归一表）"
              % (caliber_line, len(tables)))
        return 0

    with open(OUT_JSON, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)

    index_count = sum(len(v) for v in tables.values())
    print("OK 已生成唯一索引清单：%s" % OUT_JSON)
    print("   表口径：%s（业务表 = 归一表剔除 %s）"
          % (caliber_line, "、".join(INFRA_TABLES)))
    print("   清单含 %d 张归一表，唯一索引 %d 条" % (len(tables), index_count))
    for name in sorted(tables):
        cols = [" + ".join(c) for c in tables[name]]
        print("   %-32s %s" % (name, " | ".join(cols)))
    print("   生成时间：%s" % datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
