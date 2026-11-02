#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""jeecg-study 平台库初始化脚本生成器

JeecgBoot 上游的全量平台脚本硬编码了库名 `jeecg-boot`（仅文件前 2 行）：
    CREATE database if NOT EXISTS `jeecg-boot` ...;
    USE `jeecg-boot`;

本脚本从**上游原文件**读取、只改库名，产出 `jeecg-study` 版本：

  02-代码开发/02-数据库/03-脚本/01-平台库初始化.sql
  02-代码开发/05-源码/01-JeecgBoot基座/jeecg-boot/db/jeecg-study-mysql.sql  ← docker 初始化用

**两份内容完全一致（MD5 相同）**，因为它们是同一次生成的两个输出：
  · **真源唯一**在 `02-代码开发/02-数据库/03-脚本/01-平台库初始化.sql`（AGENTS.md 硬约束）；
  · 基座 `db/` 下那份是 **docker 镜像构建的派生产物**（`jeecg-boot/db/Dockerfile` 有
    `COPY ./jeecg-study-mysql.sql /docker-entrypoint-initdb.d`），已加入基座 `.gitignore`
    ——**不入库**，避免 7.1 MB 重复。**docker 构建前必须先跑一次本脚本**。

**上游原文件保持不动**，便于日后对照 v3.9.2 升级。

用法：
  python 05-公共资源\\2026103018-平台库脚本.py
  python 05-公共资源\\2026103018-平台库脚本.py --inspect   # 只看统计，不写文件

退出码：0 = 成功；2 = 上游脚本缺失或库名行数异常
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join(ROOT, "02-代码开发", "05-源码", "01-JeecgBoot基座")
SRC = os.path.join(BASE, "jeecg-boot", "db", "jeecgboot-mysql-5.7.sql")
OUT_MAIN = os.path.join(ROOT, "02-代码开发", "02-数据库", "03-脚本", "01-平台库初始化.sql")
OUT_DOCKER = os.path.join(BASE, "jeecg-boot", "db", "jeecg-study-mysql.sql")

OLD_DB = "jeecg-boot"
NEW_DB = "jeecg-study"

HEADER = """-- ============================================================
-- jeecg-study · 平台库初始化脚本
-- 由 05-公共资源\\2026103018-平台库脚本.py 从 JeecgBoot 上游
--   jeecg-boot/db/jeecgboot-mysql-5.7.sql 生成（只改库名，其余原样）
-- 上游文件保持不动，便于对照 v3.9.2 升级。
-- 目标库：jeecg-study（MySQL 8）· 平台表 133 张（sys_* / qiankun_* / onl_* / airag_* 等）
-- 本脚本**不含** study_* 业务表 —— 业务表见 02-schema.sql
-- ============================================================

"""


def main():
    if not os.path.exists(SRC):
        print("MISSING 上游脚本: %s" % SRC)
        print("提示：基座在 02-代码开发/05-源码/01-JeecgBoot基座/，"
              "先拉取 v3.9.2 再运行本脚本。")
        return 2

    with open(SRC, "r", encoding="utf-8", errors="replace") as fh:
        text = fh.read()

    lines = text.split("\n")
    db_lines = [i for i, l in enumerate(lines)
                if re.match(r"\s*(CREATE\s+database|USE\s+)", l, re.I)]
    tables = len(re.findall(r"^CREATE TABLE", text, re.M))
    inserts = len(re.findall(r"^INSERT INTO", text, re.M))

    print("上游脚本: %s" % os.path.relpath(SRC, ROOT))
    print("  库名相关行: %d 处（行号 %s）" % (len(db_lines), [i + 1 for i in db_lines]))
    print("  CREATE TABLE: %d 个 · INSERT INTO: %d 条" % (tables, inserts))

    if len(db_lines) != 2:
        print("FAIL: 库名行数异常（期望 2 行），上游脚本格式可能变了，需人工确认")
        return 2

    if "--inspect" in sys.argv:
        return 0

    for i in db_lines:
        lines[i] = re.sub(r"`%s`" % re.escape(OLD_DB), "`%s`" % NEW_DB, lines[i], flags=re.I)
    # 注释里的 Source Schema 也顺手改掉，免得以后看文件时被误导
    text = "\n".join(lines).replace("Source Schema         : %s" % OLD_DB,
                                    "Source Schema         : %s" % NEW_DB)

    payload = HEADER + text

    os.makedirs(os.path.dirname(OUT_MAIN), exist_ok=True)
    for p in (OUT_MAIN, OUT_DOCKER):
        with open(p, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(payload)
        print("OK %s (%.1f MB)" % (os.path.relpath(p, ROOT),
                                   os.path.getsize(p) / 1024 / 1024))

    # 自检：产物里不能再有旧库名的建库/切库语句
    bad = [i + 1 for i, l in enumerate(payload.split("\n"))
           if re.match(r"\s*(CREATE\s+database|USE\s+)\s*`?%s" % re.escape(OLD_DB), l, re.I)]
    if bad:
        print("FAIL: 产物仍含旧库名建库/切库语句，行号 %s" % bad)
        return 2
    print("自检: 产物中无旧库名的 CREATE DATABASE / USE 语句")
    return 0


if __name__ == "__main__":
    sys.exit(main())
