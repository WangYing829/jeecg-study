<#
    jeecg-study · 数据库凭据轮换器

    一次完成两件事（**顺序不能反**）：
      1. 用**旧口令**（从加密凭据文件里读）连库，ALTER USER 改成新口令；
      2. 把**新口令**重新加密写回凭据文件。

    为什么旧口令从文件读、而不是要求人再输一次：
      轮换的常态是「只有新口令在手」；让人再提供旧口令等于让轮换多一道人工依赖。

    用法：
      # 交互输入（推荐：口令不进命令行、不进历史）
      & '.\02-代码开发\05-源码\04-建库迁移\set-db-credential.ps1'

      # 或由调用方传入（口令已在别处）
      & '.\...\set-db-credential.ps1' -Password (Read-Host -AsSecureString '新口令')

    注意：.ps1 含中文，必须存为 UTF-8 带 BOM，否则 PowerShell 5.1 按 GBK 解码会吞换行符。
#>

param(
    [SecureString]$Password,
    [string]$StoreDir = 'C:\ProgramData\jeecg-study\credentials',
    [string]$DbHost = '127.0.0.1',
    [int]$Port = 3306,
    [string]$User = 'root',
    [string]$Database = 'jeecg-study',
    [switch]$SkipDatabase
)

$ErrorActionPreference = 'Stop'

# DPAPI（ProtectedData）在 PS 5.1 不会自动加载 System.Security 程序集
Add-Type -AssemblyName System.Security -ErrorAction SilentlyContinue

# ------------------------------------------------------------------ 取新口令

if (-not $Password) {
    # ① 优先交互输入（口令不进命令行、不进历史）
    # ② 其次取环境变量（自动化场景：口令已在环境变量里，不重复出现在命令行）
    if ($env:DB_PASSWORD) {
        Write-Host '新口令取自 $env:DB_PASSWORD'
        $Password = ConvertTo-SecureString $env:DB_PASSWORD -AsPlainText -Force
    } else {
        $Password = Read-Host '请输入新的数据库口令' -AsSecureString
    }
}

# SecureString → 明文（仅在内存中短暂存在；用完立刻擦）
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Password)
$newPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)

if ([string]::IsNullOrWhiteSpace($newPassword)) {
    throw '新口令为空'
}
if ($newPassword -match "['`"]") {
    throw "新口令不能含单引号或双引号（SQL 拼接会歧义）"
}
if ($newPassword -match '\s') {
    throw '新口令不能含空白'
}

$encFile = Join-Path $StoreDir 'db-password.enc'

# ------------------------------------------------------------------ 改库

if (-not $SkipDatabase) {
    $mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
    if (-not (Test-Path $mysql)) {
        throw "找不到 mysql 客户端：$mysql"
    }

    # 旧口令：优先从加密凭据读；没有就退回环境变量
    $oldPassword = $null
    if (Test-Path $encFile) {
        $oldCipher = [Convert]::FromBase64String(
            [IO.File]::ReadAllText($encFile, [Text.Encoding]::UTF8).Trim())
        $oldPlain = [Security.Cryptography.ProtectedData]::Unprotect($oldCipher, $null, 'LocalMachine')
        $oldPassword = [Text.Encoding]::UTF8.GetString($oldPlain)
        [Array]::Clear($oldPlain, 0, $oldPlain.Length)
    } elseif ($env:DB_PASSWORD) {
        $oldPassword = $env:DB_PASSWORD
    } else {
        throw "既没有旧口令（凭据文件不存在），也没有 $env:DB_PASSWORD —— 无法连接数据库改口令"
    }

    # 用 -e 传 SQL：口令走 MYSQL_PWD 环境变量，不出现在命令行
    $env:MYSQL_PWD = $oldPassword
    $sql = "ALTER USER '$User'@'localhost' IDENTIFIED WITH 'caching_sha2_password' BY '$newPassword';"
    $out = & $mysql -h $DbHost -P $Port -u $User -D $Database -e $sql 2>&1
    $code = $LASTEXITCODE
    $env:MYSQL_PWD = $null          # 清掉进程副本，别把口令留在环境里
    if ($code -ne 0) {
        throw "改库失败（mysql 退出码 $code）：$out"
    }
    Write-Host "[1/2] 数据库口令已更新（$User@$DbHost/$Database）"
} else {
    Write-Host '[1/2] 跳过改库（-SkipDatabase）'
}

# ------------------------------------------------------------------ 写凭据文件

if (-not (Test-Path $StoreDir)) {
    New-Item -ItemType Directory -Path $StoreDir -Force | Out-Null
}

$bytes = [Text.Encoding]::UTF8.GetBytes($newPassword)
# LocalMachine（机器级）作用域：管理员与 SYSTEM 可解密
$cipher = [Security.Cryptography.ProtectedData]::Protect($bytes, $null, 'LocalMachine')
[Array]::Clear($bytes, 0, $bytes.Length)

[IO.File]::WriteAllText($encFile, [Convert]::ToBase64String($cipher), (New-Object Text.UTF8Encoding($false)))

# 收紧 ACL：只留 Administrators 与 SYSTEM
try {
    $acl = Get-Acl -Path $encFile
    $acl.SetAccessRuleProtection($true, $false)   # 继承关掉，保留显式规则
    foreach ($rule in @($acl.Access)) { [void]$acl.RemoveAccessRule($rule) }
    foreach ($who in @('BUILTIN\Administrators', 'NT AUTHORITY\SYSTEM')) {
        $acl.AddAccessRule((New-Object System.Security.AccessControl.FileSystemAccessRule(
            $who, 'FullControl', 'Allow')))
    }
    Set-Acl -Path $encFile -AclObject $acl
    Write-Host '[2/2] 凭据文件已写入并收紧 ACL（Administrators + SYSTEM）'
} catch {
    Write-Host "[2/2] 凭据文件已写入，但 ACL 收紧失败（需人工检查）：$($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host "凭据文件：$encFile"
Write-Host '提示：仓库内不存在任何明文口令；应用与脚本通过 load-db-credential.ps1 取值。'

$newPassword = $null
