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
    [switch]$SkipFrontend,
    [string]$RuntimeDomainUrl = ''
)

$ErrorActionPreference = 'Stop'

# ── 运行期配置改写 ────────────────────────────────────────────────
# `_app.config.js` 是上游**刻意**生成的运行期覆盖文件
# （基座 `build/script/buildConf.ts` 注释原文：「生成外部配置文件，用于生产发布后配置，无需重新打包」）。
#
# 构建期写进去的是 `.env.production` 的默认值 `http://127.0.0.1:8080/jeecg-boot`
# ⇒ 部署到别的后端端口时，前端的 WebSocket 会去连 127.0.0.1:8080 ⇒ **连不上**：
#   · 每页一条控制台错误（ERR_CONNECTION_REFUSED）；
#   · 顶部消息通知功能不可用。
# 实测（2026-11-05 首次发布演练）：对着发布候选取证，**95/102 个页面**带这条 WebSocket 错误。
#
# ⇒ 部署前必须按目标环境改写它。传 -RuntimeDomainUrl 即可，不传则**打印醒目警告**，
#    让人工确认 —— 绝不能默默带着构建期默认值上线。
function Set-RuntimeDomainUrl {
    param([string]$DistDir, [string]$Url)
    $cfg = Join-Path $DistDir '_app.config.js'
    if (-not (Test-Path $cfg)) { return $false }
    $txt = [System.IO.File]::ReadAllText($cfg)
    $new = [regex]::Replace($txt, '"VITE_GLOB_DOMAIN_URL":"[^"]*"', ('"VITE_GLOB_DOMAIN_URL":"' + $Url + '"'))
    if ($new -ne $txt) {
        [System.IO.File]::WriteAllText($cfg, $new, (New-Object System.Text.UTF8Encoding($false)))
        return $true
    }
    return $false
}

if ($RuntimeDomainUrl) {
    Write-Host "运行期接口地址：$RuntimeDomainUrl"
} else {
    Write-Host '⚠ 未指定 -RuntimeDomainUrl：产物的 VITE_GLOB_DOMAIN_URL 仍是构建期默认值'
    Write-Host '  （http://127.0.0.1:8080/jeecg-boot）⇒ WebSocket 会连不上，通知功能不可用。'
    Write-Host '  部署到非 8080 的后端时，务必手动改写 dist/_app.config.js。'
}

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
    # 原生命令的 stderr（JVM warning 等）在 PS 5.1 下会被包成 ErrorRecord，
    # 在 $ErrorActionPreference = 'Stop' 下会**直接中断脚本** => 构建假失败。
    # 判据必须是退出码，不是 ErrorActionPreference。
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    # -DskipTests=true 必须显式带值：study 模块 pom 里 surefire 的 skipTests 已改成
    # ${skipTests}（默认 false），命令行才能覆盖它。
    # 构建**不跑测试**是有意为之：构建要能在「没有数据库口令」的环境下成功，
    # 测试要连真实库 —— 混在一起会让构建成败取决于机器上有没有注入口令
    # （实测：无口令时 dataSource 创建失败 ⇒ 227 条 error ⇒ 构建假红）。
    # 测试请单独跑 `mvn test`：
    #   `mvn -f <基座>/jeecg-boot/pom.xml -pl jeecg-boot-module/jeecg-module-study -am test`
    # （2026-11-05 实测：这条命令约需 2 分钟；surefire 报告落在
    #   jeecg-boot-module/jeecg-module-study/target/surefire-reports/，39 个测试类 / 357 用例）
    # ⚠ **Python 闸门不跑 Java 测试**：`2026103016-红线断言检查.py` 只做**静态断言**
    #   （只 import csv/os/re/sys，不碰 surefire）⇒ 别把它当成「测试也过了」的凭据。
    #   CI 里 Java 测试是**独立 step**（见 .github/workflows/ci.yml 的「Java 测试（真连库）」）。
    # -pl 必须指向**启动模块** jeecg-system-start，不能指向业务模块 jeecg-module-study。
    # 原因：可执行 fat jar 由 spring-boot-starter-parent 绑定的 repackage goal 生成，
    # 只有**声明了 spring-boot-maven-plugin 的启动模块**才会被 repackage。
    # 之前 -pl 指向 study ⇒ system-start 根本没进 reactor（study 不依赖它）⇒
    # 产物里那份「jeecg-system-start-3.9.2.jar」是上一轮的**薄 jar**
    # （54 个 entry、无 BOOT-INF/lib、MANIFEST 无 Main-Class）⇒ `java -jar` 直接报
    # 「jar 中没有主清单属性」。改成启动模块后，-am 会把 study 作为依赖一并构建。
    & mvn -pl jeecg-module-system/jeecg-system-start -am clean package -DskipTests=true 2>&1 |
        Tee-Object -FilePath $backendLog | Out-Null
    $mvnExit = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    if ($mvnExit -ne 0) {
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
        # 同上：stderr 不断言成败，只看退出码
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & pnpm build 2>&1 | Tee-Object -FilePath $frontLog | Out-Null
        $pnpmExit = $LASTEXITCODE
        $ErrorActionPreference = $prevEap
        if ($pnpmExit -ne 0) {
            Get-Content $frontLog -Tail 50
            throw '前端构建失败'
        }
    } finally { Pop-Location }
    Write-Host "  前端完成（$frontLog）"
} else {
    Write-Host '=== 跳过前端构建（-SkipFrontend）==='
    # 跳过前端 = 不同步 dist。此时 latest 里的前端产物是**上一轮留下的**，
    # 而版本目录会照样把它快照进去 ⇒ 20261105.085009 就是这么把一份**少 1389 个 chunk**
    # 的残缺产物当成发布候选存下来的（E-51）。这里必须核一遍，不一致就中止。
    $frontDistChk = Join-Path $srcRoot '01-JeecgBoot基座\jeecgboot-vue3\dist'
    $latestDistChk = Join-Path $latest 'frontend\dist'
    if ((Test-Path $frontDistChk) -and (Test-Path $latestDistChk)) {
        $srcC = (Get-ChildItem $frontDistChk -Recurse -File -ErrorAction SilentlyContinue).Count
        $latC = (Get-ChildItem $latestDistChk -Recurse -File -ErrorAction SilentlyContinue).Count
        if ($srcC -ne $latC) {
            throw ("前端产物不一致：latest {0} 个文件 vs 源 {1} 个（-SkipFrontend 不会同步 dist）⇒ 禁止把 latest 快照成发布候选，请先跑一次完整构建" -f $latC, $srcC)
        }
        Write-Host ("  前端产物核对通过（latest {0} 个文件 = 源）" -f $latC)
    } else {
        Write-Host '  未找到可核对的前端产物目录（源或 latest 缺失）'
    }
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
        # ① 等源目录「写完」：vite build 的 chunk 写出**不保证全部 await 完**，
        #    实测（2026-11-05）源 dist 的文件数会在 build 返回后**继续增长**。
        #    同步之前必须等它稳定，否则 robocopy 会按「当下的快照」拷走一部分 ⇒
        #    产物**缺按需加载 chunk**（首屏正常、点菜单才报错），而构建全程报成功、退出码 0。
        Write-Host '  等待源 dist 写入稳定…'
        $prev = -1
        for ($i = 0; $i -lt 30; $i++) {
            Start-Sleep -Seconds 3
            $now = (Get-ChildItem $frontDist -Recurse -File -ErrorAction SilentlyContinue).Count
            if ($now -eq $prev) { break }
            Write-Host ("    文件数 {0} → {1}" -f $prev, $now)
            $prev = $now
        }
        $srcCount = (Get-ChildItem $frontDist -Recurse -File -ErrorAction SilentlyContinue).Count
        # 运行期配置改写要在**同步之前**做，否则 /MIR 会把构建期默认值拷进 latest
        if ($RuntimeDomainUrl) {
            if (Set-RuntimeDomainUrl -DistDir $frontDist -Url $RuntimeDomainUrl) {
                Write-Host ("  已改写源 dist 的 VITE_GLOB_DOMAIN_URL = {0}" -f $RuntimeDomainUrl)
            } else {
                Write-Host '  ⚠ 源 dist 里没找到 _app.config.js，跳过运行期配置改写'
            }
        }
        $n = Sync-Dir -From $frontDist -To (Join-Path $latest 'frontend\dist')
        Write-Host ("  前端 dist 同步完成（{0} 个文件，源 {1}）" -f $n, $srcCount)

        # ② 产物完整性判据：同步后文件数必须与源**完全相等**，且 index.html 引用的资源必须都在。
        #    这条判据是 E-51 的直接补丁 —— 没有它，一个「少了 1389 个 chunk」的产物
        #    能一路带着 BUILD_EXIT=0 走到发布，而任何只看首页的检查都发现不了。
        if ($n -ne $srcCount) {
            throw ("前端产物不完整：同步后 {0} 个文件，源 {1} 个，差 {2} 个 ⇒ 拷贝被截断，禁止发布" -f $n, $srcCount, ($srcCount - $n))
        }
        $idx = Join-Path $latest 'frontend\dist\index.html'
        if (-not (Test-Path $idx)) {
            throw '前端产物不完整：dist\index.html 不存在，禁止发布'
        }
        $html = [System.IO.File]::ReadAllText($idx)
        $miss = @()
        foreach ($m in [regex]::Matches($html, '(?:src|href)\s*=\s*["'']([^"'']+)["'']')) {
            $ref = $m.Groups[1].Value
            if ($ref -match '^(https?:|//|data:|#)') { continue }
            $ref = ($ref -split '[?#]')[0]
            if (-not $ref -or $ref -eq '/') { continue }
            $p = Join-Path (Join-Path $latest 'frontend\dist') ($ref.TrimStart('/').Replace('/', '\'))
            if (-not (Test-Path $p)) { $miss += $ref }
        }
        if ($miss.Count -gt 0) {
            throw ("前端产物不完整：index.html 引用的 {0} 个资源缺失（例：{1}），禁止发布" -f $miss.Count, ($miss | Select-Object -First 3 -join ', '))
        }
        Write-Host '  前端产物完整性校验通过（文件数一致 + index.html 引用齐全）'

        # ③ 运行期配置判据：改了就要**验**改了，不接受「以为改了」
        $latestCfg = Join-Path (Join-Path $latest 'frontend\dist') '_app.config.js'
        if ($RuntimeDomainUrl) {
            if (-not (Test-Path $latestCfg)) {
                throw '前端产物不完整：dist\_app.config.js 不存在，禁止发布'
            }
            $cfgTxt = [System.IO.File]::ReadAllText($latestCfg)
            if ($cfgTxt -notmatch [regex]::Escape('"VITE_GLOB_DOMAIN_URL":"' + $RuntimeDomainUrl + '"')) {
                throw ("前端运行期配置未生效：dist\_app.config.js 里没有 VITE_GLOB_DOMAIN_URL={0}，禁止发布" -f $RuntimeDomainUrl)
            }
            Write-Host ("  运行期配置校验通过（VITE_GLOB_DOMAIN_URL = {0}）" -f $RuntimeDomainUrl)
        }
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
        runtimeDomainUrl = $RuntimeDomainUrl
        frontendFiles = (Get-ChildItem (Join-Path $latest 'frontend\dist') -Recurse -File -ErrorAction SilentlyContinue).Count
    } | ConvertTo-Json | Set-Content -Path (Join-Path $verDir 'manifest.json') -Encoding UTF8
    Write-Host "  版本目录：versions\$stamp"
}

Write-Host "完成：$stamp"
Write-Host "产物：$latest"
