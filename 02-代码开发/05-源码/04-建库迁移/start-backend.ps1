<#
    jeecg-study · 后端启动包装器（供 nssm / Windows 服务调用）

    为什么需要这个包装器（而不是 nssm 直接起 java）
    --------------------------------------------------
    ① **口令不落 nssm 配置**：nssm 的 AppEnvironmentExtra 会把口令**明文写进注册表**
       （HKLM\SYSTEM\CurrentControlSet\Services\<服务名>\Parameters）⇒ 违反 ADR-046
       「口令不落文件、不进命令行、不进注册表」。
       这里改成：启动时**点号调用** load-db-credential.ps1，从 DPAPI 加密凭据真源
       解密到**本进程环境变量**（进程退出即消失）。
    ② **jar 名带版本号**：build-and-sync.ps1 产出的 jar 是
       `jeecg-system-start-<版本>.jar`（带版本号），写死文件名会随版本漂移
       —— 部署方案 §5.4 原来的命令写的是 `jeecg-system-start.jar`，照抄会起不来。
       这里用通配找**最新**一个。
    ③ **Java 路径不写死**：优先 $env:JAVA_HOME，其次 PATH 里的 java。
       （部署方案 §5.4 写死了 `C:\Program Files\Java\jdk-17\bin\java.exe`，
        本机实际在 `D:\DevTools\Java\jdk-17.0.2`，照抄同样起不来。）

    用法
    ----
    # ① 前台起（调试用）
    powershell -NoProfile -ExecutionPolicy Bypass -File .\start-backend.ps1

    # ② 交给 nssm 托管（推荐，见部署方案 §5.4）
    nssm install study-backend powershell.exe "-NoProfile -ExecutionPolicy Bypass -File <本脚本绝对路径>"
    nssm set study-backend AppEnvironmentExtra JAVA_HOME=<JDK 目录>
    nssm start study-backend

    注意：.ps1 含中文，必须存为 UTF-8 带 BOM，否则 PowerShell 5.1 按 GBK 解码会吞换行符。
#>

param(
    # Spring profile：dev = 端口 8590（部署方案 §1.1 的 DEV 环境）
    [string]$Profile = 'dev',
    [int]$Xms = 512,
    [int]$Xmx = 1024
)

$ErrorActionPreference = 'Stop'

$scriptDir = $PSScriptRoot
# 脚本位于 <仓库根>\02-代码开发\05-源码\04-建库迁移\ ⇒ 上溯三层到仓库根
$repoRoot = (Resolve-Path (Join-Path $scriptDir '..\..\..')).Path
$backendDir = Join-Path $repoRoot '02-代码开发\05-源码\builds\latest\backend'

# ── ① 数据库口令：从 DPAPI 加密凭据真源解密，注入本进程环境变量 ──────────
$credentialLoader = Join-Path $scriptDir 'load-db-credential.ps1'
if (-not (Test-Path $credentialLoader)) {
    throw "凭据加载器不存在：$credentialLoader"
}
. $credentialLoader

# ── ② 后端 jar：取构建产物里最新的 jeecg-system-start*.jar ───────────────
if (-not (Test-Path $backendDir)) {
    throw ("构建产物目录不存在：{0}`n" +
           "先跑一次：& '.\02-代码开发\05-源码\04-建库迁移\build-and-sync.ps1'") -f $backendDir
}
$jarFile = Get-ChildItem $backendDir -Filter 'jeecg-system-start*.jar' -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jarFile) {
    throw "未找到后端 jar：$backendDir\jeecg-system-start*.jar"
}

# ── ③ java 可执行文件：$env:JAVA_HOME 优先，其次 PATH ────────────────────
$javaExe = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
} else {
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCommand) { $javaExe = $javaCommand.Source }
}
if (-not $javaExe) {
    throw '找不到 java：$env:JAVA_HOME 未设且 PATH 里没有 java。nssm 托管时请先 nssm set <服务> AppEnvironmentExtra JAVA_HOME=<JDK 目录>'
}

Write-Host '=== jeecg-study 后端启动 ==='
Write-Host ("jar     : {0}" -f $jarFile.Name)
Write-Host ("java    : {0}" -f $javaExe)
Write-Host ("profile : {0}" -f $Profile)
Write-Host '数据库口令：已从 DPAPI 加密凭据真源注入本进程环境变量（不落盘、不进命令行、不进注册表）'

& $javaExe "-Xms${Xms}m" "-Xmx${Xmx}m" '-Dfile.encoding=UTF-8' "-Dspring.profiles.active=$Profile" -jar $jarFile.FullName
exit $LASTEXITCODE
