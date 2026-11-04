<#
    jeecg-study · 数据库凭据加载器

    真源：C:\ProgramData\jeecg-study\credentials\db-password.enc
          （**DPAPI LocalMachine（机器级）作用域**加密的字节；仓库内不存在任何明文口令，
            机器上也不存在明文副本 —— 进程环境变量、注册表、配置文件里都没有）

    用法：
      # ① 注入当前会话（**点号调用**）⇒ 之后 $env:DB_PASSWORD 可用
      . '.\02-代码开发\05-源码\04-建库迁移\load-db-credential.ps1'

      # ② 只要明文（普通调用 ⇒ 输出到管道，供脚本捕获）
      $pwd = & '.\02-代码开发\05-源码\04-建库迁移\load-db-credential.ps1'

    谁在用：
      - monthly-partition.ps1：月度分表任务（Windows 计划任务）取口令的兜底来源
      - 启动后端前手动注入：`. .\load-db-credential.ps1` 再起应用
      - set-db-credential.ps1：轮换时读取旧口令

    注意：.ps1 含中文，必须存为 UTF-8 带 BOM，否则 PowerShell 5.1 按 GBK 解码会吞换行符。
#>

param(
    # 凭据存储目录（约定路径，不在版本库里）
    [string]$StoreDir = 'C:\ProgramData\jeecg-study\credentials'
)

$ErrorActionPreference = 'Stop'

# DPAPI（ProtectedData）在 PS 5.1 不会自动加载 System.Security 程序集
Add-Type -AssemblyName System.Security -ErrorAction SilentlyContinue

$encFile = Join-Path $StoreDir 'db-password.enc'
if (-not (Test-Path $encFile)) {
    throw ("凭据文件不存在：{0}`n" +
           "先跑一次轮换脚本写入：& '.\02-代码开发\05-源码\04-建库迁移\set-db-credential.ps1' -Password <新口令>") -f $encFile
}

$b64 = [IO.File]::ReadAllText($encFile, [Text.Encoding]::UTF8).Trim()
if ([string]::IsNullOrEmpty($b64)) {
    throw "凭据文件为空：$encFile"
}

$cipher = [Convert]::FromBase64String($b64)
# LocalMachine（机器级）作用域：管理员与 SYSTEM 可解密；普通用户拿不到明文
$plain = [Security.Cryptography.ProtectedData]::Unprotect($cipher, $null, 'LocalMachine')
$password = [Text.Encoding]::UTF8.GetString($plain)
[Array]::Clear($plain, 0, $plain.Length)

if ([string]::IsNullOrEmpty($password)) {
    throw "解密结果为空：$encFile（凭据文件可能已损坏，请重新写入）"
}

# 点号调用 ⇒ 注入当前会话；普通调用 ⇒ 输出明文
if ($MyInvocation.InvocationName -eq '.') {
    $env:DB_PASSWORD = $password
} else {
    Write-Output $password
}
