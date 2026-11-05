<#
    jeecg-study · nginx 启停脚本

    为什么必须有这个脚本（不是「方便」，是「绕不开」）：
      nginx 在 Windows 下**读不了带中文的路径** ——
      把仓库里的 `04-运营部署\01-部署文档\02-nginx.conf` 直接喂给 nginx 会报：
        CreateFile() "D:/.../02-nginx.conf" failed
        (1113: No mapping for the Unicode character exists in the target multi-byte code page)
      ⇒ 必须先把配置**复制到纯 ASCII 路径**（nginx 的 conf 目录）再启动。
      本脚本还负责：**先 `-t` 校验语法**，不通过就不启动（避免起一个半残的实例占着端口）。

    仓库里的那份配置是**真源**（版本化、可评审）；运行时以 nginx conf 目录里那份为准。

    用法：
      & '.\02-代码开发\05-源码\04-建库迁移\start-nginx.ps1'          # 复制 + 校验 + 启动
      & '.\02-代码开发\05-源码\04-建库迁移\start-nginx.ps1' -Stop   # 停止本项目的实例
      & '.\02-代码开发\05-源码\04-建库迁移\start-nginx.ps1' -Reload # 改完配置后重载

    注意：.ps1 含中文，必须存为 UTF-8 带 BOM，否则 PowerShell 5.1 按 GBK 解码会吞换行符。
#>

param(
    # nginx 安装目录（本机实际路径；本项目**不重装** nginx，只复用它）
    [string]$NginxHome = 'C:\Users\Administrator\Documents\nginx-1.20.2',

    # 停止 / 重载
    [switch]$Stop,
    [switch]$Reload,

    # 复制到 nginx conf 目录里的文件名（纯 ASCII）
    [string]$ConfName = 'jeecg-study.conf'
)

$ErrorActionPreference = 'Stop'

# ── 定位仓库里的配置真源 ────────────────────────────────────────────
# 本脚本在 <repo>\02-代码开发\05-源码\04-建库迁移\ ⇒ 上三级是仓库根
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$repoConf = Join-Path $repoRoot ('04-运营部署\01-部署文档\02-nginx.conf')
if (-not (Test-Path $repoConf)) {
    throw ("仓库里的 nginx 配置不存在：{0}" -f $repoConf)
}

$nginxExe = Join-Path $NginxHome 'nginx.exe'
if (-not (Test-Path $nginxExe)) {
    throw ("找不到 nginx.exe：{0}（用 -NginxHome 指定本机 nginx 安装目录）" -f $nginxExe)
}

# 运行时配置：必须是**纯 ASCII 路径**（nginx 读不了中文路径）
$confDir = Join-Path $NginxHome 'conf'
$runtimeConf = Join-Path $confDir $ConfName
$prefix = $NginxHome -replace '\\', '/'

function Invoke-NginxWait([string[]]$Arguments) {
    <#
        跑一个**自己会退出**的 nginx 子命令（-t 语法校验 / -s stop / -s reload）。
        读管道拿输出是安全的 —— 进程退出了，管道自然 EOF。
    #>
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $nginxExe
    # ⚠ PowerShell 5.1 跑在 .NET Framework 上 ⇒ **ProcessStartInfo.ArgumentList 不存在**
    #   （那是 .NET Core 2.1+ 才有的）⇒ 只能拼 Arguments 字符串。
    #   参数里没有空格，仍逐个加引号，避免以后出现带空格的路径时被拆词。
    $psi.Arguments = (($Arguments | ForEach-Object { '"' + $_ + '"' }) -join ' ')
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $p = [System.Diagnostics.Process]::Start($psi)
    $out = $p.StandardOutput.ReadToEnd()
    $err = $p.StandardError.ReadToEnd()
    $p.WaitForExit()
    return @{ Code = $p.ExitCode; Out = $out; Err = $err }
}

function Start-NginxDetached([string[]]$Arguments) {
    <#
        **启动** nginx：Windows 上 nginx 主进程**常驻前台**（不像 Unix 会 fork 后退出）
        ⇒ 绝对不能 WaitForExit / ReadToEnd：
          · WaitForExit ⇒ 脚本永远等下去；
          · 把 stdout 重定向到管道再 ReadToEnd ⇒ 派生的工作进程也持有管道写端，
            永远等不到 EOF，脚本同样挂死（2026-11-05 实测踩过）。
        正解：**重定向到文件**（文件句柄不阻塞父进程管道）+ **不等待**。
    #>
    $outFile = Join-Path $env:TEMP 'nginx-jeecg-cmd.out.log'
    $errFile = Join-Path $env:TEMP 'nginx-jeecg-cmd.err.log'
    $argLine = (($Arguments | ForEach-Object { '"' + $_ + '"' }) -join ' ')
    $p = Start-Process -FilePath $nginxExe -ArgumentList $argLine -NoNewWindow -PassThru `
            -RedirectStandardOutput $outFile -RedirectStandardError $errFile
    return @{ Code = 0; Pid = $p.Id; Out = (Get-Content $outFile -Raw -ErrorAction SilentlyContinue); Err = (Get-Content $errFile -Raw -ErrorAction SilentlyContinue) }
}

if ($Stop) {
    $r = Invoke-NginxWait @('-p', $prefix, '-c', ($runtimeConf -replace '\\', '/'), '-s', 'stop')
    Write-Host ("停止 nginx（本项目实例）：exit={0}" -f $r.Code)
    if ($r.Out) { Write-Host $r.Out.Trim() }
    if ($r.Err) { Write-Host $r.Err.Trim() }
    exit $r.Code
}

if ($Reload) {
    $r = Invoke-NginxWait @('-p', $prefix, '-c', ($runtimeConf -replace '\\', '/'), '-s', 'reload')
    Write-Host ("重载 nginx（本项目实例）：exit={0}" -f $r.Code)
    if ($r.Out) { Write-Host $r.Out.Trim() }
    if ($r.Err) { Write-Host $r.Err.Trim() }
    exit $r.Code
}

# ── 1) 复制配置到纯 ASCII 路径 ─────────────────────────────────────
Copy-Item -LiteralPath $repoConf -Destination $runtimeConf -Force
Write-Host ("已复制配置 → {0}" -f $runtimeConf)

# ── 2) 先校验语法：不通过就不启动 ───────────────────────────────────
$check = Invoke-NginxWait @('-t', '-p', $prefix, '-c', ($runtimeConf -replace '\\', '/'))
if ($check.Out) { Write-Host $check.Out.Trim() }
if ($check.Err) { Write-Host $check.Err.Trim() }
if ($check.Code -ne 0) {
    throw ("nginx 配置校验失败（exit={0}），已中止启动" -f $check.Code)
}

# ── 3) 启动（**不等待**：Windows 上 nginx 主进程常驻前台）──────────
$start = Start-NginxDetached @('-p', $prefix, '-c', ($runtimeConf -replace '\\', '/'))
if ($start.Out) { Write-Host $start.Out.Trim() }
if ($start.Err) { Write-Host $start.Err.Trim() }

Start-Sleep -Seconds 2
Write-Host ''
Write-Host '已启动。入口：'
Write-Host '  前端（静态产物）  http://127.0.0.1:8090/'
Write-Host '  后端（反代）      http://127.0.0.1:8090/jeecgboot/…  →  127.0.0.1:8590/jeecg-boot/…'
Write-Host '  日志              <nginxHome>\logs\access-jeecg.log / error-jeecg.log'
Write-Host '  停止              .\start-nginx.ps1 -Stop'
