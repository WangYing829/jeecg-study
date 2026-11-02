#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""jeecg-study DDL 提取器

从《数据库设计说明书》Markdown 源稿中提取 SQL 代码块，生成可执行的脚本文件：

  02-代码开发/02-数据库/03-脚本/02-schema.sql     ← 建库 + 业务表 DDL（真源）
  02-代码开发/02-数据库/03-脚本/03-init.sql       ← 种子数据（源稿目前只有规格，脚本占位）
  02-代码开发/02-数据库/03-脚本/04-fix.sql        ← 增量修复（从源稿的 ALTER 块生成）
  02-代码开发/02-数据库/03-脚本/05-partition.sql  ← 分表脚本（每月建分区表）
  02-代码开发/02-数据库/03-脚本/06-cleanup.sql    ← 种子数据清理脚本

幂等策略（由本脚本在生成时**强制落地**，源稿不用写幂等语法）：

  1. 建表    CREATE TABLE `x`                     → CREATE TABLE IF NOT EXISTS `x`
  2. 改结构  ALTER TABLE `t` ADD COLUMN `c` ...  → information_schema 判定 + PREPARE 动态执行
            ALTER TABLE `t` ADD KEY `k` (...)     → 同上（查 information_schema.STATISTICS）
     原因：MySQL 的 `ADD COLUMN IF NOT EXISTS` 需要 8.0.29+，本项目目标环境下限是 8.0。

用法：
  python 05-公共资源\\2026103017-DDL提取.py            # 生成
  python 05-公共资源\\2026103017-DDL提取.py --inspect  # 只看抽取结果，不写文件

退出码：0 = 成功；2 = 抽取到 0 张表（源稿格式变了，需人工介入）
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "02-代码开发", "02-数据库", "01-数据库设计说明书.md")
OUT_DIR = os.path.join(ROOT, "02-代码开发", "02-数据库", "03-脚本")

HEADER = """-- ============================================================
-- jeecg-study · %s
-- 由 05-公共资源\\2026103017-DDL提取.py 从《数据库设计说明书》源稿生成
-- 目标库：jeecg-study（MySQL 8）· 表前缀 study_
-- 幂等：建表用 IF NOT EXISTS；改结构用 information_schema 判定后动态执行
-- ============================================================

"""

FIX_HEADER = """-- ============================================================
-- jeecg-study · 增量修复脚本 fix.sql
-- **人工维护**，DDL 提取器不会覆盖本文件。
-- 规则：只追加，不改历史；每次追加写清日期、原因、影响范围。
-- 幂等要求：可重复执行不报错、不重复生效。
-- ============================================================

"""

# 只认「行首 CREATE TABLE `表名`」——避免把字符串拼接里的 DDL 误判成建表
CREATE_RE = re.compile(r"^\s*CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`(\w+)`", re.I | re.M)

# 行首建表（用于加 IF NOT EXISTS）
_CREATE_LINE_RE = re.compile(r"^(\s*)CREATE\s+TABLE\s+(?!IF\s+NOT\s+EXISTS\s)(\S+)", re.I)

# ALTER TABLE ... ADD [COLUMN] `col` ...
_ADD_COL_RE = re.compile(
    r"^\s*ALTER\s+TABLE\s+`(?P<table>\w+)`\s+ADD\s+(?:COLUMN\s+)?`(?P<col>\w+)`(?P<rest>.*)$",
    re.I,
)

# ALTER TABLE ... ADD [UNIQUE] KEY|INDEX `idx` ...
_ADD_IDX_RE = re.compile(
    r"^\s*ALTER\s+TABLE\s+`(?P<table>\w+)`\s+ADD\s+(?:UNIQUE\s+)?(?:KEY|INDEX)\s+`(?P<idx>\w+)`(?P<rest>.*)$",
    re.I,
)


def read(path):
    with open(path, "r", encoding="utf-8") as fh:
        return fh.read()


def blocks(text):
    return re.findall(r"```sql\n(.*?)```", text, re.S)


# --------------------------------------------------------------------------
# 幂等改写
# --------------------------------------------------------------------------
def make_create_idempotent(block):
    """CREATE TABLE `x` → CREATE TABLE IF NOT EXISTS `x`（只改行首，避开字符串里的 DDL）"""
    out = []
    for line in block.split("\n"):
        # 只在 "CREATE TABLE " 后插入 IF NOT EXISTS，**保留该行其余部分**
        line = re.sub(r"^(\s*CREATE\s+TABLE\s+)(?!IF\s+NOT\s+EXISTS\s)",
                      r"\1IF NOT EXISTS ", line, count=1, flags=re.I)
        out.append(line)
    return "\n".join(out)


def _split_statements(block):
    """按分号切分语句，保留原行"""
    stmts, cur = [], []
    for line in block.split("\n"):
        cur.append(line)
        if line.rstrip().endswith(";"):
            stmts.append("\n".join(cur))
            cur = []
    if any(l.strip() for l in cur):
        stmts.append("\n".join(cur))
    return [s for s in stmts if s.strip()]


def _sql_literal(text):
    """把一段 SQL 变成可放进单引号字符串的字面量（单引号翻倍）"""
    # PREPARE 的语句体不能带尾分号
    text = text.strip()
    while text.endswith(";"):
        text = text[:-1].rstrip()
    return text.replace("'", "''")


def _guard_block(table, name, kind, ddl_sql):
    """生成 information_schema 判定 + PREPARE 动态执行的幂等块

    kind = 'COLUMN' → 查 information_schema.COLUMNS
    kind = 'INDEX'  → 查 information_schema.STATISTICS
    """
    if kind == "COLUMN":
        probe = (
            "(SELECT COUNT(*) FROM information_schema.COLUMNS\n"
            "       WHERE TABLE_SCHEMA = DATABASE()\n"
            "         AND TABLE_NAME   = '%s'\n"
            "         AND COLUMN_NAME = '%s') = 0" % (table, name)
        )
        note = "幂等加列：MySQL 8.0.29 之前不支持 ADD COLUMN IF NOT EXISTS，改用 information_schema 判定"
    else:
        probe = (
            "(SELECT COUNT(*) FROM information_schema.STATISTICS\n"
            "       WHERE TABLE_SCHEMA = DATABASE()\n"
            "         AND TABLE_NAME   = '%s'\n"
            "         AND INDEX_NAME   = '%s') = 0" % (table, name)
        )
        note = "幂等加索引：先查 information_schema.STATISTICS 是否已存在"
    return (
        "-- %s\n"
        "SET @study_ddl := (\n"
        "  SELECT IF(\n"
        "    %s,\n"
        "    '%s',\n"
        "    'SELECT 1'\n"
        "  )\n"
        ");\n"
        "PREPARE study_stmt FROM @study_ddl;\n"
        "EXECUTE study_stmt;\n"
        "DEALLOCATE PREPARE study_stmt;" % (note, probe, _sql_literal(ddl_sql))
    )


def make_alter_idempotent(block):
    """ALTER ADD COLUMN / ADD KEY → information_schema 判定 + 动态执行"""
    out = []
    for stmt in _split_statements(block):
        m = _ADD_COL_RE.match(stmt.strip().split("\n")[0]) if stmt.strip() else None
        if m:
            ddl = "ALTER TABLE `%s` ADD COLUMN `%s`%s" % (
                m.group("table"), m.group("col"), m.group("rest"))
            out.append(_guard_block(m.group("table"), m.group("col"), "COLUMN", ddl))
            continue
        m = _ADD_IDX_RE.match(stmt.strip().split("\n")[0]) if stmt.strip() else None
        if m:
            ddl = "ALTER TABLE `%s` ADD KEY `%s`%s" % (
                m.group("table"), m.group("idx"), m.group("rest"))
            out.append(_guard_block(m.group("table"), m.group("idx"), "INDEX", ddl))
            continue
        out.append(stmt.rstrip())
    return "\n\n".join(out)


# --------------------------------------------------------------------------
def main():
    if not os.path.exists(SRC):
        print("MISSING %s" % SRC)
        return 1
    text = read(SRC)
    bs = blocks(text)

    create_db, ddl, alter, partition, cleanup = [], [], [], [], []
    for b in bs:
        up = b.upper()
        if "CREATE DATABASE" in up:
            create_db.append(b)
        elif re.search(r"CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+`'\s*,", b):
            # 动态拼接的分表 DDL（SET @ddl = CONCAT('CREATE TABLE IF NOT EXISTS `', ...))
            partition.append(b)
        elif "ALTER TABLE" in up:
            # 增量改造 → 走 04-fix.sql，不污染建表脚本
            alter.append(b)
        elif "CREATE TABLE" in up:
            ddl.append(b)
        elif "DELETE FROM" in up or "TRUNCATE" in up:
            cleanup.append(b)

    names = []
    for b in ddl:
        names.extend(CREATE_RE.findall(b))

    print("sql 代码块 %d 个 → 建库 %d / 建表 %d / 增量ALTER %d / 分表 %d / 清理 %d"
          % (len(bs), len(create_db), len(ddl), len(alter), len(partition), len(cleanup)))
    print("抽取到静态表 %d 张：%s" % (len(names), ", ".join(names)))

    if "--inspect" in sys.argv:
        return 0
    if not names:
        print("FAIL: 没抽到任何 CREATE TABLE，源稿格式可能变了")
        return 2

    os.makedirs(OUT_DIR, exist_ok=True)

    # 库名从建库语句里取；每个脚本自带 USE，不依赖调用方预先选库
    db_name = "jeecg-study"
    for c in create_db:
        m = re.search(r"CREATE\s+DATABASE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([\w-]+)`?", c, re.I)
        if m:
            db_name = m.group(1)
            break
    use_stmt = "USE `%s`;" % db_name

    def write(name, header, chunks, tail=""):
        p = os.path.join(OUT_DIR, name)
        with open(p, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(header)
            for c in chunks:
                fh.write(c.rstrip() + "\n\n")
            if tail:
                fh.write(tail)
        print("OK %s (%d blocks)" % (os.path.relpath(p, ROOT), len(chunks)))

    # 建表脚本：统一加 IF NOT EXISTS；建库块自带 USE，缺失则补
    schema_chunks = [make_create_idempotent(c) for c in create_db]
    if not any(re.search(r"^\s*USE\s", c, re.I | re.M) for c in schema_chunks):
        schema_chunks.append(use_stmt)
    schema_chunks += [make_create_idempotent(c) for c in ddl]
    write("02-schema.sql", HEADER % "建表脚本 schema.sql —— DDL 真源", schema_chunks,
          "SET FOREIGN_KEY_CHECKS = 1;\n")

    write("03-init.sql", HEADER % "种子数据 init.sql", [use_stmt],
          "-- 《数据库设计说明书》§11 目前只给了种子数据的**规格**（类别 / 数量 / 原则），\n"
          "-- 尚无具体 INSERT 语句。落地时按下列规格补齐，并保证幂等：\n"
          "--\n"
          "--   教材版本 7 项（人教/苏教/北师大/浙教/沪教/京教/鲁教）      → F-REG-03\n"
          "--   全局上下文 1 条（省 + 学段 + 年级 + 教材版本）\n"
          "--   坐标树 ≥ 200 节点（一个年级的数学学科完整四层）\n"
          "--   母题 ≥ 20（小学应用题，覆盖 ≥ 5 个题型）\n"
          "--   单词 ≥ 300（课内某单元 + 课外高频词）\n"
          "--   语法点 ≥ 20（含中考题型标注）\n"
          "--   写作拆解 ≥ 10（六层各 ≥ 1 条）\n"
          "--   题目 ≥ 100（覆盖全部题型）\n"
          "--\n"
          "-- 原则：版权安全（自编或公开授权）/ 只映射结构不复制教材正文 /\n"
          "--       种子数据带 SEED_ 前缀可一键清理 / 幂等 / 不含真实个人信息\n")

    fix = os.path.join(OUT_DIR, "04-fix.sql")
    if alter:
        # 源稿里有增量改造 → 整份重写（fix.sql 是脚本产物，不是人工手写的）
        with open(fix, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(HEADER % "增量修复脚本 fix.sql —— 由设计源稿生成")
            fh.write(use_stmt + "\n\n")
            for b in alter:
                fh.write(make_alter_idempotent(b).rstrip() + "\n\n")
        print("OK %s (%d alter blocks)" % (os.path.relpath(fix, ROOT), len(alter)))
    elif not os.path.exists(fix):
        with open(fix, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(FIX_HEADER)
            fh.write(use_stmt + "\n\n")
        print("OK %s (新建占位)" % os.path.relpath(fix, ROOT))
    else:
        print("SKIP %s (人工维护，未覆盖)" % os.path.relpath(fix, ROOT))

    write("05-partition.sql", HEADER % "分表脚本 partition.sql", [use_stmt] + partition)
    write("06-cleanup.sql", HEADER % "种子数据清理脚本 cleanup.sql", [use_stmt] + cleanup)

    return 0


if __name__ == "__main__":
    sys.exit(main())
