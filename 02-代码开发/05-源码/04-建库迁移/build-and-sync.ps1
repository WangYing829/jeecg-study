<#
    jeecg-study · 构建与产物同步执行器

    用法：
      .\build-and-sync.ps1                    # 构建后端 + 前端，同步到 05-源码\builds\latest
      .\build-and-sync.ps1 -SkipVersion       # 不同步版本号（不写 builds\versions\<stamp>）
      .\build-and-sync.ps1 -SkipFrontend      # 只构建后端

    行为：
      1. 后端：mvn -pl jeecg-module-study -am clean package -DskipTests
      2. 前端：pnpm build（产物 dist/）
      3. SQL：把真源目录镜像到构建产物
      4. 同步：builds\latest\<backend|frontend|sql>（增量镜像，不整拷）

    两个必须遵守的坑（来自既有工程的教训）：
      ① 构建输出**不要**经 | Out-Host 管道转发 —— 1MB+ 资源清单会把管道压垮并让构建**静默中断**。
         用 Tee-Object -FilePath $log | Out-Null，失败时打印日志尾部。
      ② Start-Process -ArgumentList 传数组不安全，会用空格拼成一条命令行且不加引号。
         调用原生命令优先用 &。

    注意：.ps1 含中文，必须存为 UTF-8 带 BOM，否则 PowerShell 5.1 按 GBK 解码会吞换行符。
#>

param(
    [switch]$SkipVersion,
    [switch]$SkipFrontend
)

$ErrorActionPreference = 'Stop'

# 05-源码 根（本脚本在 05-源码\04-建库迁移\ 下）
$srcRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repoRoot = (Resolve-Path (Join-Path $srcRoot '..\..')).Path

$stamp = Get-Date -Format 'yyyyMMdd.HHmmss'
$logDir = Join-Path $srcRoot '03-构建发布\build-logs'
$buildsRoot = Join-Path $srcRoot 'builds'
$latest = Join-Path $buildsRoot 'latest'

New-Item -ItemType Directory -Path $logDir -Force | Out-Null
New-Item -ItemType Directory -Path $latest -Force | Out-Null

Write-Host '=== jeecg-study 构建与同步 ==='
Write-Host "源码根: $srcRoot"
Write-Host "产物根: $buildsRoot"

# ── 1) 后端 ───────────────────────────────────────────────────────
Write-Host '=== 构建后端 ==='
$backendLog = Join-Path $logDir "backend-$stamp.log"
$backendRoot = Join-Path $srcRoot '01-JeecgBoot基座\jeecg-boot'
Push-Location $backendRoot
try {
    & mvn -pl jeecg-boot-module/jeecg-module-study -am clean package -DskipTests 2>&1 |
        Tee-Object -FilePath $backendLog | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Get-Content $backendLog -Tail 50
        throw '后端构建失败'
    }
} finally { Pop-Location }
Write-Host "  后端完成（$backendLog）"

# ── 2) 前端 ───────────────────────────────────────────────────────
if (-not $SkipFrontend) {
    Write-Host '=== 构建前端 ==='
    $frontLog = Join-Path $logDir "frontend-$stamp.log"
    $frontRoot = Join-Path $srcRoot '01-JeecgBoot基座\jeecgboot-vue3'
    Push-Location $frontRoot
    try {
        & pnpm build 2>&1 | Tee-Object -FilePath $frontLog | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Get-Content $frontLog -Tail 50
            throw '前端构建失败'
        }
    } finally { Pop-Location }
    Write-Host "  前端完成（$frontLog）"
} else {
    Write-Host '=== 跳过前端构建（-SkipFrontend）==='
}

# ── 3) SQL 真源镜像 ───────────────────────────────────────────────
Write-Host '=== 镜像 SQL 真源 ==='
$sqlSrc = Join-Path $repoRoot '02-代码开发\02-数据库\03-脚本'
$sqlDst = Join-Path $latest 'sql'
if (Test-Path $sqlSrc) {
    New-Item -ItemType Directory -Path $sqlDst -Force | Out-Null
    Copy-Item -Path (Join-Path $sqlSrc '*.sql') -Destination $sqlDst -Force
    Copy-Item -Path (Join-Path $sqlSrc '*.md')  -Destination $sqlDst -Force -ErrorAction SilentlyContinue
    Write-Host ("  已镜像 {0} 个文件" -f (Get-ChildItem $sqlDst -File).Count)
} else {
    Write-Host '  跳过（SQL 真源目录不存在）'
}

# ── 4) 同步产物到 latest（增量镜像，不整拷）─────────────────────────
Write-Host '=== 同步产物 ==='

function Sync-Dir {
    param([string]$From, [string]$To)
    if (-not (Test-Path $From)) { return 0 }
    New-Item -ItemType Directory -Path $To -Force | Out-Null
    # robocopy：/MIR 镜像（删除目标多余文件），/NFL /NDL 静默文件列表
    & robocopy $From $To /MIR /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "同步失败：$From → $To（robocopy exit=$LASTEXITCODE）" }
    return (Get-ChildItem $To -Recurse -File -ErrorAction SilentlyContinue).Count
}

# 后端产物：模块 jar + 启动模块 jar + 运行配置
$backendOut = Join-Path $latest 'backend'
New-Item -ItemType Directory -Path $backendOut -Force | Out-Null
$jarCandidates = @(
    (Join-Path $backendRoot 'jeecg-boot-module\jeecg-module-study\target\jeecg-module-study-3.9.2.jar'),
    (Join-Path $backendRoot 'jeecg-module-system\jeecg-system-start\target\jeecg-system-start-3.9.2.jar')
)
foreach ($jar in $jarCandidates) {
    if (Test-Path $jar) {
        Copy-Item $jar -Destination $backendOut -Force
        Write-Host ("  jar: {0}" -f (Split-Path $jar -Leaf))
    }
}
# 运行配置（application-*.yml）
$cfgDir = Join-Path $backendRoot 'jeecg-module-system\jeecg-system-start\src\main\resources'
if (Test-Path $cfgDir) {
    Get-ChildItem $cfgDir -Filter 'application-*.yml' -File | ForEach-Object {
        Copy-Item $_.FullName -Destination $backendOut -Force
    }
    Write-Host '  配置文件已同步（application-*.yml）'
}
New-Item -ItemType Directory -Path (Join-Path $backendOut 'logs') -Force | Out-Null

# 前端产物
if (-not $SkipFrontend) {
    $frontDist = Join-Path $srcRoot '01-JeecgBoot基座\jeecgboot-vue3\dist'
    if (Test-Path $frontDist) {
        $n = Sync-Dir -From $frontDist -To (Join-Path $latest 'frontend\dist')
        Write-Host ("  前端 dist 同步完成（{0} 个文件）" -f $n)
    } else {
        Write-Host '  跳过（dist 不存在）'
    }
}

# ── 5) 版本目录 ───────────────────────────────────────────────────
if (-not $SkipVersion) {
    $verDir = Join-Path $buildsRoot "versions\$stamp"
    New-Item -ItemType Directory -Path $verDir -Force | Out-Null
    foreach ($sub in @('backend', 'frontend', 'sql')) {
        $from = Join-Path $latest $sub
        if (Test-Path $from) {
            Copy-Item $from -Destination $verDir -Recurse -Force
        }
    }
    # 写版本清单
    @{
        version    = $stamp
        builtAt    = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
        backendJar = (Get-ChildItem $backendOut -Filter '*.jar' -File -ErrorAction SilentlyContinue |
                      ForEach-Object { $_.Name }) -join ', '
        sqlFiles   = (Get-ChildItem (Join-Path $latest 'sql') -Filter '*.sql' -File -ErrorAction SilentlyContinue).Count
    } | ConvertTo-Json | Set-Content -Path (Join-Path $verDir 'manifest.json') -Encoding UTF8
    Write-Host "  版本目录：versions\$stamp"
}

Write-Host "完成：$stamp"
Write-Host "产物：$latest"
