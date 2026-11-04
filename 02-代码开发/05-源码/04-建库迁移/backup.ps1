<#
    jeecg-study · 备份执行器

    用法：
      .\backup.ps1 -Env prod            # 备份业务库 + 平台库 + 错题图片
      .\backup.ps1 -Env dev             # 只备份 dev 业务库
      .\backup.ps1 -Env prod -KeepDays 60

    行为：
      1. 用 mysqldump 导出各库 → .sql → GZipStream 压缩 → .sql.gz
      2. 错题图片目录（唯一不在数据库里的业务数据）单独 robocopy 增量复制
      3. 保留策略：只保留最近 N 天的备份

    口令：不写入脚本、不进命令行。三段来源（与 monthly-partition.ps1 同口径）：
      MYSQL_PWD → DB_PASSWORD → 加密凭据真源（DPAPI）

    注意：.ps1 含中文，必须存为 UTF-8 带 BOM，否则 PowerShell 5.1 按 GBK 解码会吞换行符。
#>

param(
    [ValidateSet('dev', 'test', 'acc', 'prod')]
    [string]$Env = 'prod',
    [int]$KeepDays = 30
)

$ErrorActionPreference = 'Stop'

# 仓库根：脚本在 <root>\02-代码开发\05-源码\04-建库迁移\ 下
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path

# ── 口令来源（三段兜底）────────────────────────────────────────────
function Resolve-DbPassword {
    if ($env:MYSQL_PWD) { return $env:MYSQL_PWD }
    if ($env:DB_PASSWORD) { return $env:DB_PASSWORD }
    $loader = Join-Path $PSScriptRoot 'load-db-credential.ps1'
    if (Test-Path $loader) {
        $pwd = & $loader
        if ($pwd) { return ("$pwd").Trim() }
    }
    throw '取不到数据库口令：请设置 MYSQL_PWD / DB_PASSWORD，或先运行 set-db-credential.ps1 写入加密凭据真源'
}

$env:MYSQL_PWD = Resolve-DbPassword

# ── 环境 → 库清单 ─────────────────────────────────────────────────
$dbs = switch ($Env) {
    'dev'  { @('jeecg-study') }
    'test' { @('jeecg-study-test') }
    'acc'  { @('jeecg-study-acc') }
    'prod' { @('jeecg-study-prod', 'jeecg-boot') }
}

$backupDir = Join-Path $root '04-运营部署\03-发布记录\backup'
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'

Write-Host "=== jeecg-study 备份 ==="
Write-Host "环境: $Env    目标目录: $backupDir"

# ── 找 mysqldump（与 mysql 客户端同目录）──────────────────────────
function Resolve-MySqlTool {
    param([string]$Name)
    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        'D:\DevTools\mysql\bin', 'C:\Program Files\MySQL', 'C:\Program Files (x86)\MySQL'
    )
    foreach ($base in $candidates) {
        if (-not (Test-Path $base)) { continue }
        $hit = Get-ChildItem -Path $base -Recurse -Filter "$Name.exe" -ErrorAction SilentlyContinue |
               Select-Object -First 1
        if ($hit) { return $hit.FullName }
    }
    throw "找不到 $Name：请把它所在目录加入 PATH"
}

$dump = Resolve-MySqlTool 'mysqldump'

# ── 压缩：优先用系统 gzip，否则退回 .NET GZipStream ───────────────
function Compress-File {
    param([string]$Src, [string]$Dst)
    $gzip = Get-Command 'gzip' -ErrorAction SilentlyContinue
    if ($gzip) {
        & $gzip.Source -9 -c $Src | Set-Content -Path $Dst -Encoding Byte -ErrorAction SilentlyContinue
        if (-not (Test-Path $Dst) -or (Get-Item $Dst).Length -eq 0) {
            Remove-Item $Dst -Force -ErrorAction SilentlyContinue
        } else {
            return $true
        }
    }
    # 退回 .NET：无外部依赖
    $in  = [IO.File]::OpenRead($Src)
    $out = [IO.File]::Create($Dst)
    try {
        $gz = New-Object IO.Compression.GZipStream($out, [IO.Compression.CompressionMode]::Compress)
        try { $in.CopyTo($gz) } finally { $gz.Dispose() }
    } finally {
        $in.Dispose(); $out.Dispose()
    }
    return $true
}

# ── 1) 数据库备份 ─────────────────────────────────────────────────
foreach ($db in $dbs) {
    $raw = Join-Path $backupDir "$db-$stamp.sql"
    $out = "$raw.gz"

    Write-Host "-> 备份 $db ..."
    & $dump --single-transaction --routines --triggers --default-character-set=utf8mb4 `
        -u root $db --result-file="$raw" 2>$null
    if ($LASTEXITCODE -ne 0) {
        Remove-Item $raw -Force -ErrorAction SilentlyContinue
        throw "备份失败：$db"
    }

    Compress-File -Src $raw -Dst $out | Out-Null
    Remove-Item $raw -Force -ErrorAction SilentlyContinue

    $mb = (Get-Item $out).Length / 1MB
    Write-Host ("  完成：{0}  {1:N2} MB" -f (Split-Path $out -Leaf), $mb)
}

# ── 2) 错题图片目录（唯一不在数据库里的业务数据）──────────────────
$imgSrc = Join-Path $root '02-代码开发\05-源码\01-JeecgBoot基座\data\upload\study'
if (-not (Test-Path $imgSrc)) {
    $imgSrc = Join-Path $root '02-代码开发\05-源码\data\upload\study'
}
if (Test-Path $imgSrc) {
    $imgDst = Join-Path $backupDir "upload-$stamp"
    & robocopy $imgSrc $imgDst /E /NFL /NDL /NJH /NJS /NP | Out-Null
    # robocopy 的退出码 <8 都算成功（1=有文件被复制）
    if ($LASTEXITCODE -ge 8) { throw "图片备份失败（robocopy exit=$LASTEXITCODE）" }
    Write-Host '  图片备份完成'
} else {
    Write-Host '  跳过图片备份（目录不存在）'
}

# ── 3) 保留策略 ───────────────────────────────────────────────────
$cutoff = (Get-Date).AddDays(-$KeepDays)
$removed = 0
Get-ChildItem $backupDir -Filter *.sql.gz -ErrorAction SilentlyContinue |
    Where-Object { $_.LastWriteTime -lt $cutoff } |
    ForEach-Object { Remove-Item $_.FullName -Force; $removed++ }
if ($removed -gt 0) { Write-Host "  清理过期备份：$removed 个" }

Write-Host "=== 备份结束：$stamp ==="
Write-Host '恢复演练：备份不做恢复演练等于没有备份 —— 见《运维手册》§2.4'
