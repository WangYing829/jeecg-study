<#
    jeecg-study · 数据库迁移执行器

    用法：
      .\migrate.ps1 -Env dev            # 建库 + 跑全部脚本
      .\migrate.ps1 -Env dev -DryRun    # 只打印将要执行什么
      .\migrate.ps1 -Env dev -Force     # 忽略迁移记录，强制重跑（危险）

    行为：
      1. 目标库不存在 → 先建库（utf8mb4 / utf8mb4_unicode_ci，与上游一致）
      2. 按脚本编号顺序执行（见下方 $scripts 的顺序说明）
      3. 每个脚本执行后写一条 study_schema_migration 记录（脚本名 + 内容 SHA-256）
      4. 已登记的脚本默认跳过（幂等）；-Force 可强制重跑
      5. 01-平台库初始化.sql 是上游 DROP+CREATE 语义，**只在平台表缺失时执行**

    脚本清单口径（2026-11-04 修正）：
      此前 $scripts 只列 01→05，**07–22 号运行时种子与菜单脚本从未被自动执行过**
      （它们是手工执行的，所以库里有效果但 study_schema_migration 里没有记录）。
      现已补全为「建表 → 增量修表 → 分表 → 运行时种子 → 菜单授权」全序列，
      **新环境按本脚本部署即可拿到与本机一致的数据**。

    库名是部署参数：脚本里的 `jeecg-study` 字面量会被替换成目标环境库名。

    注意：.ps1 含中文，必须存为 UTF-8 带 BOM，否则 PowerShell 5.1 按 GBK 解码会吞换行符。
#>

param(
    [ValidateSet('dev', 'test', 'acc', 'prod')]
    [string]$Env = 'dev',
    [switch]$DryRun,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

$mysql = 'mysql'
$db = switch ($Env) {
    'dev' { 'jeecg-study' }
    'test' { 'jeecg-study-test' }
    'acc' { 'jeecg-study-acc' }
    'prod' { 'jeecg-study-prod' }
}

# 仓库根：脚本在 <root>\02-代码开发\05-源码\04-建库迁移\ 下
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$sqlDir = Join-Path $root '02-代码开发\02-数据库\03-脚本'

if (-not (Test-Path $sqlDir)) {
    throw "找不到 SQL 真源目录：$sqlDir"
}
if (-not $env:MYSQL_PWD) {
    throw "环境变量 MYSQL_PWD 未设置（不把口令写进脚本或命令行）"
}

function Invoke-Sql {
    param([string]$Sql)
    $out = & $mysql -u root -h 127.0.0.1 -P 3306 -N -B -e $Sql 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "SQL 失败：$Sql`n$out"
    }
    return $out
}

function Invoke-Script {
    param([string]$Path)
    # 库名替换：脚本里的 jeecg-study 字面量 → 目标环境库名
    #
    # ⚠ 三个必须避开的坑（2026-11-04 实测踩到）：
    #   ① **不能把 SQL 用管道喂给 mysql**（`$sql | & mysql`）——
    #      PowerShell 5.1 会把管道当对象按 ANSI 编码写出，中文注释变成 ????? 直接语法错。
    #   ② **不能把含 BOM 的文件直接喂给 mysql**——BOM 会被当成 SQL 内容，
    #      报 `You have an SQL syntax error ... near '﻿'`。
    #   ⇒ 正确做法：**去掉 BOM → 写临时文件 → cmd 重定向导入**，字节完全可控。
    $raw = [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8)
    # 去掉可能存在的 BOM 字符（U+FEFF）
    $raw = $raw.TrimStart([char]0xFEFF)
    $sql = $raw -replace '(?i)\bjeecg-study\b', $db

    $tmp = [IO.Path]::GetTempFileName()
    try {
        # 用 UTF8Encoding($false) 写：**不带 BOM**
        [IO.File]::WriteAllText($tmp, $sql, (New-Object Text.UTF8Encoding($false)))
        $cmd = "mysql -u root -h 127.0.0.1 -P 3306 --default-character-set=utf8mb4 --database=$db < `"$tmp`""
        & cmd.exe /c $cmd 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "脚本失败：$Path（mysql exit=$LASTEXITCODE）"
        }
    } finally {
        Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    }
}

function Get-Checksum {
    param([string]$Path)
    return (Get-FileHash -Path $Path -Algorithm SHA256).Hash.ToLower()
}

Write-Host "=== jeecg-study 数据库迁移 ==="
Write-Host "环境: $Env    目标库: $db    SQL 真源: $sqlDir"
if ($DryRun) { Write-Host '[DryRun] 只演示，不实际执行' }

# 1) 建库（不存在才建）
$exists = Invoke-Sql "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$db';"
if ("$exists".Trim() -eq '0') {
    Write-Host "-> 建库 $db"
    if (-not $DryRun) {
        Invoke-Sql "CREATE DATABASE IF NOT EXISTS ``$db`` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;"
    }
} else {
    Write-Host "-> 库 $db 已存在"
}

# 2) 迁移记录表（脚本 02 会建，这里先确保可查）
if (-not $DryRun) {
    Invoke-Sql @"
CREATE TABLE IF NOT EXISTS ``$db``.``study_schema_migration`` (
  ``id`` varchar(32) NOT NULL,
  ``script_name`` varchar(128) NOT NULL,
  ``checksum`` char(64) NOT NULL,
  ``applied_at`` datetime NOT NULL,
  ``duration_ms`` int DEFAULT NULL,
  ``del_flag`` tinyint NOT NULL DEFAULT 0,
  ``create_by`` varchar(50) DEFAULT NULL,
  ``create_time`` datetime DEFAULT NULL,
  ``update_by`` varchar(50) DEFAULT NULL,
  ``update_time`` datetime DEFAULT NULL,
  PRIMARY KEY (``id``),
  UNIQUE KEY ``uk_script_name`` (``script_name``)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
"@
}

# 3) 逐个执行
#
# 执行顺序说明（依赖链）：
#   01 平台库初始化   —— 平台表 133 张 + 种子（上游 DROP+CREATE，只在平台表缺失时跑）
#   02 schema         —— 业务表 DDL（study_ 前缀）
#   03 init           —— 业务种子占位
#   04 fix            —— 增量：行为表加 child_id
#   05 partition      —— 分表（当月表）
#   07 role-menu      —— 角色 + 菜单 + 按钮 + 角色授权（study_kid / study_parent）
#   08 account        —— 演示账号 + 家庭 + 亲子关系
#   09 content        —— 坐标树 + 母题 + 题目 + 词表（业务完整版）
#   12 writing-scenario —— 写作情境库表 + 种子
#   14 grammar-context-game —— 语法语境库 + 趣味玩法表 + 种子
#   16 writing-scenario-menu —— 写作情境页菜单
#   17 grammar-game-menu     —— 语法玩法页菜单
#   18 grammar-point          —— 语法点种子（11 个语法点 + 五态语料）
#   19 admin-study-menu      —— admin 授权全部 study 菜单 + 挂进演示家庭 A
#   20 fix-menu-icon-name    —— 修正失效菜单图标名
#   21 fix-study-menu-always-show —— study 一级菜单 always_show 归 0
#   22 study-interface-pages-menu —— §11.2 新增 13 页的菜单 + 自愈回写
#   23 notification-preference-and-ai-feedback —— 家长提醒偏好表 + 解析申诉表
#   24 study-planned-pages-menu  —— 21 个规划页（含 1 个组件）+ 2 个家长端拆分页的菜单
#
#   ⚠ 06 / 13 / 15 是 **cleanup** 脚本（清理种子数据），**不在迁移序列里**——
#     它们是「回滚/清库」用的，不能在建库流程里跑，否则会把刚灌的种子删掉。
#
#   ⚠ 20 / 21 是「修正型」脚本：它们修的是 07/17/22 写进去的行。
#     幂等（可重复执行），但**必须排在对应种子之后**。
$scripts = @(
    '01-平台库初始化.sql',
    '02-schema.sql',
    '03-init.sql',
    '04-fix.sql',
    '05-partition.sql',
    '07-seed-role-menu.sql',
    '08-seed-account.sql',
    '09-seed-content.sql',
    '12-writing-scenario.sql',
    '14-grammar-context-game.sql',
    '16-seed-writing-scenario-menu.sql',
    '17-seed-grammar-game-menu.sql',
    '18-seed-grammar-point.sql',
    '19-seed-admin-study-menu.sql',
    '20-fix-menu-icon-name.sql',
    '21-fix-study-menu-always-show.sql',
    '22-seed-study-interface-pages-menu.sql',
    '23-seed-notification-preference-and-ai-feedback.sql',
    '24-seed-study-planned-pages-menu.sql'
)

foreach ($name in $scripts) {
    $path = Join-Path $sqlDir $name
    if (-not (Test-Path $path)) {
        Write-Host "跳过 $name（文件不存在）"
        continue
    }

    # 01 是上游 DROP+CREATE 语义：平台表已存在就不再跑（否则会抹掉 sys_user 等运行数据）
    if ($name -eq '01-平台库初始化.sql') {
        $n = Invoke-Sql "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$db' AND TABLE_NAME='sys_user';"
        if ("$n".Trim() -ne '0') {
            Write-Host "跳过 $name（平台表已存在；重跑会 DROP 重建并抹掉运行数据）"
            continue
        }
    }

    $sum = Get-Checksum $path
    $applied = Invoke-Sql "SELECT COUNT(*) FROM ``$db``.``study_schema_migration`` WHERE script_name='$name' AND checksum='$sum';"

    if ("$applied".Trim() -ne '0' -and -not $Force) {
        Write-Host "跳过 $name（已执行且校验和一致）"
        continue
    }
    if ("$applied".Trim() -ne '0' -and $Force) {
        Write-Host "强制重跑 $name（校验和已变：已登记内容与当前文件不一致）" -ForegroundColor Yellow
    }

    Write-Host "执行 $name ..."
    if ($DryRun) { continue }

    $sw = [Diagnostics.Stopwatch]::StartNew()
    try {
        Invoke-Script -Path $path
    } catch {
        Write-Host "失败：$name" -ForegroundColor Red
        throw
    }
    $sw.Stop()

    Invoke-Sql @"
INSERT INTO ``$db``.``study_schema_migration``
  (``id``, ``script_name``, ``checksum``, ``applied_at``, ``duration_ms``, ``create_by``, ``create_time``)
VALUES
  (REPLACE(UUID(),'-',''), '$name', '$sum', NOW(), $($sw.ElapsedMilliseconds), '$Env', NOW())
ON DUPLICATE KEY UPDATE ``checksum``=VALUES(``checksum``), ``applied_at``=VALUES(``applied_at``),
  ``duration_ms``=VALUES(``duration_ms``), ``update_by``=VALUES(``create_by``), ``update_time``=NOW();
"@
    Write-Host "  完成（$($sw.ElapsedMilliseconds) ms）"
}

Write-Host '=== 迁移结束 ==='
if (-not $DryRun) {
    Write-Host '已登记记录：'
    Invoke-Sql "SELECT script_name, checksum, applied_at FROM ``$db``.``study_schema_migration`` ORDER BY applied_at;" | ForEach-Object { Write-Host "  $_" }
}
