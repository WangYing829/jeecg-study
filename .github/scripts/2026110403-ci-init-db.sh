#!/usr/bin/env bash
# ============================================================================
# jeecg-study · CI 步骤②：建库 + 灌种子（migrate.ps1 的 bash 等价物）
#
# ## 为什么要有 bash 版
#
# `02-代码开发/05-源码/04-建库迁移/migrate.ps1` 是 **Windows PowerShell 脚本**，
# CI 跑在 ubuntu-latest 上，**不能调用任何 .ps1**（本仓库约定：CI 只用 bash）。
# 本脚本是它的等价实现，脚本顺序、库名替换、幂等口径、迁移登记**逐条对齐**。
#
# ## 三个必须避开的坑（Windows 侧实测踩到过，Linux 侧同样要避开）
#
#   ① **不能用管道把 SQL 喂给 mysql**（`cat x.sql | mysql`）——
#      分隔符会被吃掉，某些客户端会截断。正确做法：**写临时文件 → `mysql < file`**。
#   ② **不能把含 BOM 的文件直接喂给 mysql**——BOM 会被当成 SQL 内容，
#      报 `You have an SQL syntax error ... near '﻿'`。
#      本脚本先用 sed 去掉 UTF-8 BOM（\xEF\xBB\xBF）。
#   ③ **库名是部署参数**：脚本里的 `jeecg-study` 字面量要替换成目标库名
#      （migrate.ps1 里是 `-replace '(?i)\bjeecg-study\b', $db`）。
#
# ## 口令怎么来
#
# 由 CI 自己生成（见 ci.yml 的 prepare job），通过环境变量 `DB_PASSWORD` 传入，
# **不落文件、不进命令行、不依赖任何仓库 secret 或个人凭据**。
# mysql 客户端的口令走 `--defaults-extra-file`（权限 600，脚本结束时删除），
# 避免口令出现在进程参数里。
#
# 退出码：0 = 迁移完成；非 0 = 失败
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SQL_DIR="$ROOT/02-代码开发/02-数据库/03-脚本"

DB="${MYSQL_DATABASE:-jeecg-study}"
HOST="${MYSQL_HOST:-127.0.0.1}"
PORT="${MYSQL_PORT:-3306}"
DBUSER="${MYSQL_USER:-root}"

if [ -z "${DB_PASSWORD:-}" ]; then
  echo "[FAIL] 环境变量 DB_PASSWORD 未设置（CI 由 prepare job 生成并注入）"
  exit 2
fi
if [ ! -d "$SQL_DIR" ]; then
  echo "[FAIL] 找不到 SQL 真源目录：$SQL_DIR"
  exit 2
fi
# 库名会被拼进 sed 替换与反引号包裹的 SQL，必须是安全的标识符
if ! printf '%s' "$DB" | grep -Eq '^[A-Za-z0-9_-]+$'; then
  echo "[FAIL] MYSQL_DATABASE 不是合法库名：$DB"
  exit 2
fi

# ---- mysql 客户端兜底：GitHub ubuntu runner 自带 mysql-client，缺了再装 ----
if ! command -v mysql >/dev/null 2>&1; then
  echo "==> 安装 mysql-client"
  sudo apt-get update -qq
  sudo apt-get install -y -qq mysql-client
fi

# ---- 口令走 defaults 文件，不进命令行 ----
#
# ⚠ **口令必须转义**：mysql 的选项文件里 `#` 与 `;` 是注释起始符，
#   值里带 `#` 会被**静默截断** → 连接报 `ERROR 1045 Access denied`。
#   （2026-11-04 本地实测踩到：真源口令含 `#`。）
#   规则：值用双引号包起来，里面的 `\` 与 `"` 各转义一次。
CNF="$(mktemp)"
cnf_pw="$(printf '%s' "$DB_PASSWORD" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g')"
cat > "$CNF" <<EOF
[client]
host=$HOST
port=$PORT
user=$DBUSER
password="$cnf_pw"
EOF
chmod 600 "$CNF"
cleanup() { rm -f "$CNF"; }
trap cleanup EXIT

mysql_cli() { mysql --defaults-extra-file="$CNF" "$@"; }

# ---- 等 MySQL 起来（GitHub 会等 healthcheck，这里再加一层兜底）----
echo "==> 等待 MySQL $HOST:$PORT"
for i in $(seq 1 60); do
  if mysqladmin --defaults-extra-file="$CNF" ping --silent >/dev/null 2>&1; then
    echo "    MySQL 已就绪"
    break
  fi
  if [ "$i" = "60" ]; then
    echo "[FAIL] MySQL 120 秒内没就绪"
    exit 3
  fi
  sleep 2
done

# ---- 建库 ----
echo "==> 建库 $DB"
mysql_cli --default-character-set=utf8mb4 -e \
  "CREATE DATABASE IF NOT EXISTS \`$DB\` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;"

# ---- 迁移记录表（与 migrate.ps1 同结构）----
mysql_cli --default-character-set=utf8mb4 -e "
CREATE TABLE IF NOT EXISTS \`$DB\`.\`study_schema_migration\` (
  \`id\` varchar(32) NOT NULL,
  \`script_name\` varchar(128) NOT NULL,
  \`checksum\` char(64) NOT NULL,
  \`applied_at\` datetime NOT NULL,
  \`duration_ms\` int DEFAULT NULL,
  \`del_flag\` tinyint NOT NULL DEFAULT 0,
  \`create_by\` varchar(50) DEFAULT NULL,
  \`create_time\` datetime DEFAULT NULL,
  \`update_by\` varchar(50) DEFAULT NULL,
  \`update_time\` datetime DEFAULT NULL,
  PRIMARY KEY (\`id\`),
  UNIQUE KEY \`uk_script_name\` (\`script_name\`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"

# ---- 脚本序列（与 migrate.ps1 的 $scripts 逐条对齐）----
#   01 平台库初始化   —— 平台表 133 张 + 种子（上游 DROP+CREATE，只在平台表缺失时跑）
#   02 schema         —— 业务表 DDL（study_ 前缀）
#   03 init           —— 业务种子占位
#   04 fix            —— 增量：行为表加 child_id
#   05 partition      —— 分表（当月表）
#   07 role-menu      —— 角色 + 菜单 + 按钮 + 角色授权
#   08 account        —— 演示账号 + 家庭 + 亲子关系
#   09 content        —— 坐标树 + 母题 + 题目 + 词表
#   12 writing-scenario / 14 grammar-context-game —— 两域的业务表 + 种子
#   16/17             —— 两页的菜单
#   18                —— 语法点种子
#   19 admin 授权 / 20 图标名修正 / 21 always_show 修正
#   22                —— §11.2 新增页菜单
#   23                —— 家长提醒偏好表 + 解析申诉表
#   24                —— 21 个规划页（含 1 个组件）+ 2 个家长端拆分页菜单
#
# ⚠ 06 / 13 / 15 是 cleanup 脚本，**不在迁移序列里**（会把刚灌的种子删掉）。
SCRIPTS=(
  "01-平台库初始化.sql"
  "02-schema.sql"
  "03-init.sql"
  "04-fix.sql"
  "05-partition.sql"
  "07-seed-role-menu.sql"
  "08-seed-account.sql"
  "09-seed-content.sql"
  "12-writing-scenario.sql"
  "14-grammar-context-game.sql"
  "16-seed-writing-scenario-menu.sql"
  "17-seed-grammar-game-menu.sql"
  "18-seed-grammar-point.sql"
  "19-seed-admin-study-menu.sql"
  "20-fix-menu-icon-name.sql"
  "21-fix-study-menu-always-show.sql"
  "22-seed-study-interface-pages-menu.sql"
  "23-seed-notification-preference-and-ai-feedback.sql"
  "24-seed-study-planned-pages-menu.sql"
)

for name in "${SCRIPTS[@]}"; do
  path="$SQL_DIR/$name"
  if [ ! -f "$path" ]; then
    echo "跳过 $name（文件不存在）"
    continue
  fi

  # 01 是上游 DROP+CREATE 语义：平台表已存在就不再跑（否则会抹掉 sys_user 等运行数据）
  if [ "$name" = "01-平台库初始化.sql" ]; then
    n="$(mysql_cli -N -B -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB' AND TABLE_NAME='sys_user';")"
    if [ "$(echo "$n" | tr -d '[:space:]')" != "0" ]; then
      echo "跳过 $name（平台表已存在；重跑会 DROP 重建并抹掉运行数据）"
      continue
    fi
  fi

  sum="$(sha256sum "$path" | awk '{print $1}')"
  applied="$(mysql_cli -N -B -e "SELECT COUNT(*) FROM \`$DB\`.\`study_schema_migration\` WHERE script_name='$name' AND checksum='$sum';")"
  if [ "$(echo "$applied" | tr -d '[:space:]')" != "0" ]; then
    echo "跳过 $name（已执行且校验和一致）"
    continue
  fi

  echo "执行 $name ..."
  # 去 BOM → 替换库名 → 写临时文件 → **文件重定向**导入（不喂管道）
  tmp_sql="$(mktemp)"
  sed -e 's/\xEF\xBB\xBF//' "$path" \
    | sed -e "s/\\bjeecg-study\\b/$DB/g" > "$tmp_sql"

  started="$(date +%s%3N)"
  mysql_cli --default-character-set=utf8mb4 --database="$DB" < "$tmp_sql"
  rm -f "$tmp_sql"
  ended="$(date +%s%3N)"
  cost=$((ended - started))

  mysql_cli --default-character-set=utf8mb4 -e "
INSERT INTO \`$DB\`.\`study_schema_migration\`
  (\`id\`, \`script_name\`, \`checksum\`, \`applied_at\`, \`duration_ms\`, \`create_by\`, \`create_time\`)
VALUES
  (REPLACE(UUID(),'-',''), '$name', '$sum', NOW(), $cost, 'ci', NOW())
ON DUPLICATE KEY UPDATE \`checksum\`=VALUES(\`checksum\`), \`applied_at\`=VALUES(\`applied_at\`),
  \`duration_ms\`=VALUES(\`duration_ms\`), \`update_by\`=VALUES(\`create_by\`), \`update_time\`=NOW();"
  echo "  完成（${cost} ms）"
done

echo "==> 已登记记录："
mysql_cli -N -B -e "SELECT script_name, applied_at FROM \`$DB\`.\`study_schema_migration\` ORDER BY applied_at;"

echo "=== CI 迁移结束 ==="
