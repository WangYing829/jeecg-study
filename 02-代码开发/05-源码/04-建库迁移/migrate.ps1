<#
    jeecg-study · 数据库迁移执行器

    用法：
      .\migrate.ps1 -Env dev            # 建库 + 跑全部脚本
      .\migrate.ps1 -Env dev -DryRun    # 只打印将要执行什么
      .\migrate.ps1 -Env dev -Force     # 忽略迁移记录，强制重跑（危险）

    行为：
      1. 目标库不存在 → 先建库（utf8mb4 / utf8mb4_unicode_ci，与上游一致）
      2. 按 01 → 02 → 03 → 04 → 05 顺序执行
      3. 每个脚本执行后写一条 study_schema_migration 记录（脚本名 + 内容 SHA-256）
      4. 已登记的脚本默认跳过（幂等）；-Force 可强制重跑
      5. 01-平台库初始化.sql 是上游 DROP+CREATE 语义，**只在平台表缺失时执行**

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
    $sql = [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8)
    $sql = $sql -replace '(?i)\bjeecg-study\b', $db
    $sql | & $mysql -u root -h 127.0.0.1 -P 3306 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "脚本失败：$Path"
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
$scripts = @(
    '01-平台库初始化.sql',
    '02-schema.sql',
    '03-init.sql',
    '04-fix.sql',
    '05-partition.sql'
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
