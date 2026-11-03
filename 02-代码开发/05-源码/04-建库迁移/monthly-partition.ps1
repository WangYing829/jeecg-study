<#
    jeecg-study · 月度分表任务执行器

    用法：
      .\monthly-partition.ps1 -Env dev              # 建当月分表
      .\monthly-partition.ps1 -Env dev -DryRun      # 只打印将要执行什么
      .\monthly-partition.ps1 -Env dev -LogPath ...  # 指定日志路径

    为什么需要它（2026-11-03 排查出的跨月隐患，ADR-045）：
      作答记录按月分表（study_practice_record_yyyyMM）。当月表由本脚本建。
      本机 238 个计划任务里**没有**对应项 —— 05-partition.sql 第 12 行注释写着
      「每月 1 日由定时任务执行（幂等）」，但那个任务并不存在。
      后果不只是「记不上分」：练习域「取一道孩子没做过的题」那条 SQL 带
      NOT EXISTS (SELECT 1 FROM ${tableName} ...) 子查询，
      当月表不存在 ⇒ **孩子连题都取不到**。

    设计约定：
      1. 口令只从环境变量读（MYSQL_PWD 优先，其次 DB_PASSWORD），
         **不写进脚本、不写进命令行、不入库**。
      2. 启动即打印「实际生效的模式 + 实际命令行」——
         外部调度（计划任务）最常见的事故是参数没传对，
         日志里能一眼看出，比在文档里写十遍「别忘了加参数」有效。
      3. 跑完**必须自证**：查 information_schema 确认当月分表真的建出来了，
         查不到就以非零码退出 —— 计划任务的 LastTaskResult 才能反映失败。
      4. 幂等：05-partition.sql 用 CREATE TABLE IF NOT EXISTS，重复跑安全。

    注意：.ps1 含中文，必须存为 UTF-8 带 BOM，否则 PowerShell 5.1 按 GBK 解码会吞换行符。
#>

param(
    [ValidateSet('dev', 'test', 'acc', 'prod')]
    [string]$Env = 'dev',
    [switch]$DryRun,
    [string]$LogPath = 'C:\ProgramData\jeecg-study\logs\monthly-partition.log'
)

$ErrorActionPreference = 'Stop'

# ------------------------------------------------------------------ 定位资源

# 仓库根：脚本在 <root>\02-代码开发\05-源码\04-建库迁移\ 下
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$sqlFile = Join-Path $root '02-代码开发\02-数据库\03-脚本\05-partition.sql'

$db = switch ($Env) {
    'dev' { 'jeecg-study' }
    'test' { 'jeecg-study-test' }
    'acc' { 'jeecg-study-acc' }
    'prod' { 'jeecg-study-prod' }
}

# 计划任务的 PATH 不含 MySQL 的 bin 目录 ⇒ 必须自己找
function Resolve-MySql {
    $candidates = @(
        (Get-Command mysql -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue),
        'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
        'C:\Program Files\MySQL\MySQL Server 8.1\bin\mysql.exe',
        'C:\xampp\mysql\bin\mysql.exe'
    ) | Where-Object { $_ -and (Test-Path $_) }
    if ($candidates.Count -eq 0) {
        throw '找不到 mysql 客户端（PATH 与常见安装目录都没有）'
    }
    return $candidates[0]
}

# ------------------------------------------------------------------ 日志

$logLines = New-Object System.Collections.Generic.List[string]

function Write-Log {
    param([string]$Text, [string]$Level = 'INFO')
    $line = ('{0} [{1}] {2}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Level, $Text)
    Write-Host $line
    $logLines.Add($line)
}

function Save-Log {
    try {
        $dir = Split-Path -Parent $LogPath
        if (-not (Test-Path $dir)) {
            New-Item -ItemType Directory -Path $dir -Force | Out-Null
        }
        Add-Content -Path $LogPath -Value $logLines -Encoding UTF8
    } catch {
        Write-Host ('写日志失败（不影响主流程）：' + $_.Exception.Message)
    }
}

# ------------------------------------------------------------------ 开工

Write-Log '=== jeecg-study 月度分表任务 ==='
Write-Log ("实际生效模式：环境={0}  DryRun={1}" -f $Env, [bool]$DryRun)
# 打印**真实**调用串（含本次实际传入的开关），不是「建议命令」——
# 外部调度最常见的事故就是参数没传对，日志里必须能一眼看出本次到底带了什么
$actualArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"{0}"' -f $PSCommandPath), '-Env', $Env)
if ($DryRun) { $actualArgs += '-DryRun' }
Write-Log ("实际命令行：powershell " + ($actualArgs -join ' '))

if (-not (Test-Path $sqlFile)) {
    throw "找不到月度分表脚本：$sqlFile"
}

$mysql = Resolve-MySql
Write-Log "mysql 客户端：$mysql"

# 口令：MYSQL_PWD 优先，其次 DB_PASSWORD（与基座 ${DB_PASSWORD:root} 对齐）
$pwd = $env:MYSQL_PWD
$src = 'MYSQL_PWD'
if ([string]::IsNullOrEmpty($pwd)) {
    $pwd = $env:DB_PASSWORD
    $src = 'DB_PASSWORD'
}
if ([string]::IsNullOrEmpty($pwd)) {
    throw '口令环境变量未设置：MYSQL_PWD 或 DB_PASSWORD 任一即可（不把口令写进脚本或命令行）'
}
Write-Log "口令来源：$src（值不打印）"

# 客户端从 MYSQL_PWD 取口令 —— 用进程级副本，不污染调用方环境
$env:MYSQL_PWD = $pwd

# 当月分表名（与 05-partition.sql 的 DATE_FORMAT(NOW(), '%Y%m') 同一口径）
$monthTable = 'study_practice_record_' + (Get-Date -Format 'yyyyMM')
Write-Log "目标库：$db    目标分表：$monthTable"

function Invoke-Sql {
    param([string]$Sql)
    $out = & $mysql -u root -h 127.0.0.1 -P 3306 -N -B -e $Sql 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "SQL 失败：$Sql`n$out"
    }
    return $out
}

$exitCode = 0
try {
    if ($DryRun) {
        Write-Log '[DryRun] 不实际执行 SQL' 'WARN'
    } else {
        # 库名替换：脚本里的 jeecg-study 字面量 → 目标环境库名
        $sql = [IO.File]::ReadAllText($sqlFile, [Text.Encoding]::UTF8)
        $sql = $sql -replace '(?i)\bjeecg-study\b', $db
        $sql | & $mysql -u root -h 127.0.0.1 -P 3306 2>&1 | ForEach-Object {
            if ("$_".Trim()) { Write-Log "mysql> $_" }
        }
        if ($LASTEXITCODE -ne 0) {
            throw "执行 05-partition.sql 失败，mysql 退出码 $LASTEXITCODE"
        }

        # 自证：查 information_schema 确认当月分表真的存在
        $n = Invoke-Sql "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$db' AND TABLE_NAME='$monthTable';"
        if ("$n".Trim() -eq '0') {
            throw "执行完仍未见分表 $db.$monthTable —— 建表没生效，必须人工介入"
        }
        $cols = Invoke-Sql "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$db' AND TABLE_NAME='$monthTable';"
        Write-Log "自证通过：$db.$monthTable 已存在（$("$cols".Trim()) 列）"
    }
    Write-Log '=== 月度分表任务结束：成功 ==='
} catch {
    Write-Log ("=== 月度分表任务结束：失败 —— " + $_.Exception.Message) 'ERROR'
    $exitCode = 1
} finally {
    Save-Log
    # 清掉进程级口令副本（不用 Remove-Item Env:，避免与文件删除策略撞车）
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $null, 'Process')
}

exit $exitCode
