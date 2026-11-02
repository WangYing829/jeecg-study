# =============================================================================
# 2026103022-端到端验证.ps1 · jeecg-study 真实运行时端到端验证（P1）
# =============================================================================
# 用途：把「自动化用例全绿但真实链路从未跑过」这个缺口补上。
#   上一轮 236 条用例里，底座能力（用户 / 角色 / Redis）是 Mock 的，
#   「登录 → 角色 → 菜单 → 权限 → 端点」这条链路在真实环境从未跑过。
#   本脚本对**真实启动的后端**逐条打真实 HTTP 请求，验证：
#     1. 家长账号密码登录（底座 POST /sys/login，走真实验证码链路）
#     2. 家长可见自己关联的孩子（child_id 隔离）
#     3. 家长端学习报告 / 错题分析
#     4. 孩子两步登录：家庭码 → 候选列表 → PIN 换 JWT
#     5. 孩子取题（**响应不得含答案字段**，R-01）
#     6. 孩子作答 → 判定 → 答错入错题池 → 复习队列（Q-02 口径）
#     7. 背单词 / 语法点 / 护眼心跳 / 数学母题列表 / 写作情境库
#     8. 语法域：语境库（F-GRM-05）+ 趣味玩法（F-GRM-04）
#     9. 越权分两层：**权限拒绝**（角色没有该权限码）与**数据域 4010**
#        （家长访问「存在但与自己无关」的孩子，统一 4010 而不是 404）
#
# 前置：后端已启动（默认 8590），种子脚本 07/08/09 已灌库，Redis 已就位。
#       数据库口令从环境变量 DB_PASSWORD 读取（未设置则回落上游默认 root）；口令不入库。
# 用法：$env:DB_PASSWORD='<本机口令>'; powershell -NoProfile -ExecutionPolicy Bypass -File .\05-公共资源\2026103022-端到端验证.ps1
# 退出码：0 = 全部通过；2 = 有用例失败。
# =============================================================================
param(
    [string]$BaseUrl = 'http://localhost:8590/jeecg-boot',
    [string]$ParentUser = 'study_parent_demo',
    [string]$ParentPass = 'study@2026',
    [string]$OtherParentUser = 'study_parent_other_demo',
    [string]$FamilyCode = 'STU026',
    [string]$Pin = '1234',
    [string]$ChildCode = 'CHD-000001',
    [string]$OtherChildCode = 'CHD-000002',
    # 数据库口令：只从环境变量读，不入库（DB_PASSWORD 未设置时回落上游默认 root）
    [string]$DbPassword = $(if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { 'root' })
)

$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)

$script:Pass = 0
$script:Fail = 0
$script:Failed = New-Object System.Collections.ArrayList

function Check {
    param([string]$Name, [bool]$Ok, [string]$Detail = '')
    if ($Ok) {
        $script:Pass++
        Write-Output ("  [PASS] {0}{1}" -f $Name, $(if ($Detail) { " — $Detail" } else { '' }))
    } else {
        $script:Fail++
        [void]$script:Failed.Add("$Name $($Detail)")
        Write-Output ("  [FAIL] {0}{1}" -f $Name, $(if ($Detail) { " — $Detail" } else { '' }))
    }
}

# 底座返回体没有 charset 头，PS 默认按 Latin-1 解会变乱码；这里统一按 UTF-8 解
function Convert-ResponseText {
    param($Resp)
    try {
        $bytes = $Resp.RawContentStream.ToArray()
        return [Text.Encoding]::UTF8.GetString($bytes)
    } catch {
        return ''
    }
}

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token = '',
        $Body = $null
    )
    $headers = @{}
    if ($Token) { $headers['X-Access-Token'] = $Token }
    $params = @{
        Method      = $Method
        Uri         = ($BaseUrl + $Path)
        Headers     = $headers
        TimeoutSec  = 30
        ContentType = 'application/json;charset=utf-8'
    }
    if ($null -ne $Body) {
        $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 8 -Compress }
        $params['Body'] = [Text.Encoding]::UTF8.GetBytes($json)
    }
    try {
        $resp = Invoke-WebRequest @params
        $text = Convert-ResponseText $resp
        if (-not $text) { return @{ __error = '空响应体' } }
        return ($text | ConvertFrom-Json)
    } catch {
        $err = $_.Exception
        $body = ''
        if ($err.Response) {
            try {
                $reader = New-Object System.IO.StreamReader($err.Response.GetResponseStream(), [Text.Encoding]::UTF8)
                $body = $reader.ReadToEnd()
            } catch { }
        }
        if ($body) {
            try {
                $parsed = $body | ConvertFrom-Json
                if ($null -ne $parsed.code) { return $parsed }
            } catch { }
        }
        return @{ __error = $err.Message }
    }
}

function Success {
    param($Resp)
    return ($null -ne $Resp -and $null -eq $Resp.__error -and $Resp.success -eq $true)
}

# 取响应体里的业务错误码（统一成字符串比较，缺省返回空串）
function CodeOf {
    param($Resp)
    if ($null -eq $Resp) { return '' }
    if ($null -eq $Resp.code) { return '' }
    return "$($Resp.code)"
}

# ------------------------------------------------------------------ 验证码
# 底座登录要图形验证码：GET /sys/randomImage/{checkKey} 生成 4 位码，
# 服务端按 md5(checkKey + signatureSecret) + 验证码（小写）作为 Redis 键存值。
# 脚本按同样规则算出键前缀，再从 Redis 里把码取出来 —— 这样走的是**真实登录链路**，
# 而不是把 jeecg.firewall.enableLoginCaptcha 关掉（那是底座的安全开关，不该为了测试关掉）。
function Get-SignatureSecret {
    $yml = 'D:\DevProject\2-JavaPrj\20261030-jeecg-study\02-代码开发\05-源码\01-JeecgBoot基座\jeecg-boot\jeecg-module-system\jeecg-system-start\src\main\resources\application-dev.yml'
    $m = Select-String -Path $yml -Pattern '^\s*signatureSecret:\s*(\S+)' | Select-Object -First 1
    if ($m) { return $m.Matches[0].Groups[1].Value }
    return 'dd05f1c54d63749eda95f9fa6d49v442a'
}

function Get-LoginCaptcha {
    $checkKey = [Guid]::NewGuid().ToString('N')
    $secret = Get-SignatureSecret
    $md5 = [System.Security.Cryptography.MD5]::Create()
    $bytes = $md5.ComputeHash([Text.Encoding]::UTF8.GetBytes($checkKey + $secret))
    $prefix = ([BitConverter]::ToString($bytes)).Replace('-', '').ToLower()
    # 触发生成
    $null = Invoke-Json -Method 'GET' -Path ('/sys/randomImage/' + $checkKey)
    # redis-cli --scan 在本机 redis-cli 上不输出内容，用 KEYS 更稳
    $keys = & redis-cli KEYS "$prefix*" 2>$null
    foreach ($k in $keys) {
        if ($k -and $k.StartsWith($prefix)) {
            return @{ checkKey = $checkKey; captcha = $k.Substring($prefix.Length) }
        }
    }
    return @{ checkKey = $checkKey; captcha = '' }
}

function Login-Platform {
    param([string]$User, [string]$Pass)
    $c = Get-LoginCaptcha
    $r = Invoke-Json -Method 'POST' -Path '/sys/login' -Body @{
        username = $User
        password = $Pass
        captcha  = $c.captcha
        checkKey = $c.checkKey
    }
    if (Success $r) { return $r.result.token }
    return ''
}

Write-Output '==================== jeecg-study 端到端验证 ===================='
Write-Output ("后端：{0}" -f $BaseUrl)
Write-Output ''

# ------------------------------------------------------------------ 0. 健康检查
Write-Output '[0] 后端存活'
$sysMenu = Invoke-Json -Method 'GET' -Path '/sys/menu/getUserMenuByToken'
# 未带 token 时返回 401 本身就是「后端活着且鉴权生效」的证据
$alive = ($null -ne $sysMenu -and $null -eq $sysMenu.__error) -or
         ($null -ne $sysMenu.__error -and $sysMenu.__error -match '401')
Check '后端已启动（未带 token 返回 401 说明鉴权生效）' $alive `
    $(if ($sysMenu.__error) { $sysMenu.__error } else { 'HTTP 200' })

# ------------------------------------------------------------------ 1. 家长登录
Write-Output ''
Write-Output '[1] 家长账号密码登录（底座 /sys/login，走真实图形验证码）'
$captcha = Get-LoginCaptcha
Check '图形验证码已获取' ([bool]$captcha.captcha) ("captcha=" + $captcha.captcha)
$parentToken = Login-Platform -User $ParentUser -Pass $ParentPass
Check '家长登录成功并拿到 token' ([bool]$parentToken) ("token 长度 " + $parentToken.Length)

$otherParentToken = Login-Platform -User $OtherParentUser -Pass $ParentPass
Check '第二个家长登录成功（用于隔离验证）' ([bool]$otherParentToken) ("token 长度 " + $otherParentToken.Length)

if (-not $parentToken) {
    Write-Output ''
    Write-Output '家长登录失败，后续用例无法执行。'
    exit 2
}

# ------------------------------------------------------------------ 2. 孩子可见性
Write-Output ''
Write-Output '[2] 家长可见自己关联的孩子（child_id 隔离）'
$children = Invoke-Json -Method 'GET' -Path '/api/v1/study/children' -Token $parentToken
$childCodes = @()
if (Success $children) { $childCodes = @($children.result | ForEach-Object { $_.childCode }) }
Check '孩子列表只含关系表内的孩子' (($childCodes -contains $ChildCode) -and $childCodes.Count -eq 1) `
    ("childCode = " + ($childCodes -join ','))

if ($otherParentToken) {
    $otherChildren = Invoke-Json -Method 'GET' -Path '/api/v1/study/children' -Token $otherParentToken
    $otherCodes = @()
    if (Success $otherChildren) { $otherCodes = @($otherChildren.result | ForEach-Object { $_.childCode }) }
    Check '另一个家长只看到自己的孩子' (($otherCodes -contains $OtherChildCode) -and $otherCodes.Count -eq 1) `
        ("childCode = " + ($otherCodes -join ','))
}

# ------------------------------------------------------------------ 3. 家长端报表
Write-Output ''
Write-Output '[3] 家长端学习报告 / 错题分析'
$overview = Invoke-Json -Method 'GET' -Path ('/api/v1/study/parent/report/overview?childCode=' + $ChildCode) -Token $parentToken
Check '学习报告总览可取' (Success $overview) $(if ($overview.result) { 'overview 已返回' } else { '无数据' })
$weekly = Invoke-Json -Method 'GET' -Path ('/api/v1/study/parent/report/weekly?childCode=' + $ChildCode) -Token $parentToken
Check '周报逐日序列可取' (Success $weekly) $(if ($weekly.result) { 'weekly 已返回' } else { '无数据' })
$errReport = Invoke-Json -Method 'GET' -Path ('/api/v1/study/parent/report/errors?childCode=' + $ChildCode) -Token $parentToken
Check '错因分布 / 薄弱考点可取' (Success $errReport) ''

# ------------------------------------------------------------------ 4. 孩子两步登录
Write-Output ''
Write-Output '[4] 孩子两步登录（家庭码 → 候选列表 → PIN）'
$lookup = Invoke-Json -Method 'POST' -Path '/api/v1/study/kid/lookup' -Body (@{ familyCode = $FamilyCode })
$candidateIds = @()
if (Success $lookup) { $candidateIds = @($lookup.result | ForEach-Object { $_.id }) }
Check '家庭码换候选孩子列表' ($candidateIds.Count -ge 1) ("候选 " + $candidateIds.Count + " 个")

$childId = ''
if ($candidateIds.Count -ge 1) { $childId = $candidateIds[0] }
$badFamily = Invoke-Json -Method 'POST' -Path '/api/v1/study/kid/lookup' -Body (@{ familyCode = 'BOGUS-FAMILY-CODE' })
Check '错误家庭码被拒且错误码为 4009' ((-not (Success $badFamily)) -and (CodeOf $badFamily) -eq '4009') `
    ("code=" + (CodeOf $badFamily) + ' ' + $badFamily.message)

$kidLogin = Invoke-Json -Method 'POST' -Path '/api/v1/study/kid/login' -Body (@{ childId = $childId; pin = $Pin })
$kidToken = ''
if (Success $kidLogin) { $kidToken = $kidLogin.result.token }
Check 'PIN 换 JWT 成功' ([bool]$kidToken) ("token 长度 " + $kidToken.Length)

$badLogin = Invoke-Json -Method 'POST' -Path '/api/v1/study/kid/login' -Body (@{ childId = $childId; pin = '9999' })
Check '错误 PIN 被拒（不泄露失败次数）' ((-not (Success $badLogin)) -and (CodeOf $badLogin) -eq '400') `
    ("code=" + (CodeOf $badLogin) + ' ' + $badLogin.message)

if (-not $kidToken) {
    Write-Output ''
    Write-Output '孩子登录失败，后续用例无法执行。'
    exit 2
}

$me = Invoke-Json -Method 'GET' -Path '/api/v1/study/kid/me' -Token $kidToken
Check '孩子身份可取（kid/me）' (Success $me) $(if ($me.result) { $me.result.childCode } else { '' })

# ------------------------------------------------------------------ 5. 取题不含答案
Write-Output ''
Write-Output '[5] 取题（红线 R-01：响应不得含答案字段）'
$q = Invoke-Json -Method 'GET' -Path '/api/v1/study/practice/math/next' -Token $kidToken
$qRaw = if ($q.result) { $q.result | ConvertTo-Json -Depth 8 -Compress } else { '' }
$forbidden = @('"answer"', '"correctAnswer"', '"analysis"', '"answerTolerance"')
$hit = @($forbidden | Where-Object { $qRaw -match [regex]::Escape($_) })
Check '取题响应不含答案/解析字段' ((Success $q) -and $hit.Count -eq 0) `
    $(if ($hit.Count -gt 0) { "命中 " + ($hit -join ',') } else { "contentId=" + $q.result.contentId })
$contentId = ''
if ($q.result) { $contentId = $q.result.contentId }

# ------------------------------------------------------------------ 6. 作答与错题池
Write-Output ''
Write-Output '[6] 作答 → 判定 → 错题入池 → 复习队列'
$session = Invoke-Json -Method 'GET' -Path '/api/v1/study/practice/math/session' -Token $kidToken
$sessionId = ''
if (Success $session) { $sessionId = $session.result.sessionId }
Check '会话可创建' ([bool]$sessionId) $sessionId

# 取一道确定答案的题：先取题，再从库里拿标准答案
$rightAnswer = ''
if ($contentId) {
    $mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
    if (Test-Path $mysql) {
        $raw = & $mysql -uroot ("-p" + $DbPassword) --default-character-set=utf8mb4 -D 'jeecg-study' -N -B `
            -e "SELECT answer FROM study_question WHERE question_code = '$contentId' LIMIT 1;" 2>&1 |
            Where-Object { $_ -notmatch 'Warning' }
        if ($raw) { $rightAnswer = ($raw | Select-Object -First 1).Trim() }
    }
}
Check '取到标准答案（用于构造正确作答）' ([bool]$rightAnswer) $rightAnswer

$wrongAnswer = '999999'

if ($rightAnswer) {
    $ok = Invoke-Json -Method 'POST' -Path '/api/v1/study/practice/answer' -Token $kidToken `
        -Body (@{ sessionId = $sessionId; contentId = $contentId; answer = $rightAnswer; contentType = 'NUMERIC' })
    Check '正确作答判定为对' ((Success $ok) -and $ok.result.correct -eq $true) `
        $(if ($ok.result) { "basis=" + $ok.result.basis } else { '' })
    Check '判定来源为规则（judge_source=rule）' ($ok.result.source -eq 'rule') ("source=" + $ok.result.source)
}

# 换一题做错（入错题池）
$q2 = Invoke-Json -Method 'GET' -Path '/api/v1/study/practice/math/next' -Token $kidToken
$q2Id = ''
if ($q2.result) { $q2Id = $q2.result.contentId }
$wrong2 = Invoke-Json -Method 'POST' -Path '/api/v1/study/practice/answer' -Token $kidToken `
    -Body (@{ sessionId = $sessionId; contentId = $q2Id; answer = $wrongAnswer; contentType = 'NUMERIC'; errorCause = '计算失误' })
Check '答错判定为错且返回错题业务ID' ((Success $wrong2) -and $wrong2.result.correct -eq $false -and [bool]$wrong2.result.errorId) `
    $(if ($wrong2.result) { "errorId=" + $wrong2.result.errorId } else { "失败：" + $wrong2.message })

# 4001 用例必须换新会话：作答幂等的键是「孩子 + 会话 + 题目」（TC-08-06），
# 同一会话重提同一题会直接重放上次的判定结果、**不再校验错因** ——
# 那样测到的就成了「幂等重放」而不是「缺错因被拒」。
$session2 = Invoke-Json -Method 'GET' -Path '/api/v1/study/practice/math/session' -Token $kidToken
$session2Id = ''
if (Success $session2) { $session2Id = $session2.result.sessionId }
$noCause = Invoke-Json -Method 'POST' -Path '/api/v1/study/practice/answer' -Token $kidToken `
    -Body (@{ sessionId = $session2Id; contentId = $q2Id; answer = $wrongAnswer; contentType = 'NUMERIC' })
Check '答错未选错因被拒（4001）' ((-not (Success $noCause)) -and (CodeOf $noCause) -eq '4001') `
    ("code=" + (CodeOf $noCause) + ' ' + $noCause.message)

$pool = Invoke-Json -Method 'GET' -Path ('/api/v1/study/error-pool?childCode=' + $ChildCode) -Token $parentToken
$poolCount = 0
if (Success $pool) { $poolCount = @($pool.result).Count }
Check '错题池里有刚才那题' ($poolCount -ge 1) ("条数 " + $poolCount)

$queue = Invoke-Json -Method 'GET' -Path '/api/v1/study/review/queue' -Token $kidToken
$queueCount = 0
if (Success $queue) { $queueCount = @($queue.result).Count }
Check '复习队列可取' (Success $queue) ("条数 " + $queueCount)

# ------------------------------------------------------------------ 7. 其余学习端
Write-Output ''
Write-Output '[7] 背单词 / 语法 / 护眼 / 母题 / 情境库'
$word = Invoke-Json -Method 'GET' -Path '/api/v1/study/word/practice/next?lineType=IN' -Token $kidToken
$wordRaw = if ($word.result) { $word.result | ConvertTo-Json -Depth 6 -Compress } else { '' }
$wordCode = ''
if ($word.result) { $wordCode = $word.result.wordCode }
Check '背单词可取词' (Success $word) $(if ($word.result) { "wordCode=" + $word.result.wordCode } else { $word.message })
Check '取词不下发拼写（R-01）' (($wordRaw -notmatch '"spelling"') -and (Success $word)) ''

$grammar = Invoke-Json -Method 'GET' -Path '/api/v1/study/grammar/points' -Token $kidToken
$grammarCount = 0
if (Success $grammar -and $grammar.result) { $grammarCount = @($grammar.result.list).Count }
Check '语法点列表可取' (Success $grammar) ("条数 " + $grammarCount)

$eye = Invoke-Json -Method 'POST' -Path '/api/v1/study/eye/session/start' -Token $kidToken
$eyeId = ''
if (Success $eye) { $eyeId = $eye.result.sessionId }
Check '护眼会话可开' ([bool]$eyeId) $eyeId
if ($eyeId) {
    $hb = Invoke-Json -Method 'POST' -Path ('/api/v1/study/eye/session/' + $eyeId + '/heartbeat') -Token $kidToken `
        -Body (@{ active = $true })
    Check '护眼心跳上报成功（R-03 服务端累计）' (Success $hb) ''
}

$mother = Invoke-Json -Method 'GET' -Path ('/api/v1/study/math/mother/list?childId=' + $childId) -Token $parentToken
$motherCount = 0
if (Success $mother) { $motherCount = @($mother.result).Count }
Check '数学母题列表可取' (Success $mother) ("条数 " + $motherCount)

$scenario = Invoke-Json -Method 'GET' -Path '/api/v1/study/writing/scenario/themes' -Token $kidToken
$scenarioCount = 0
if (Success $scenario -and $scenario.result) { $scenarioCount = @($scenario.result.items).Count }
Check '写作情境库主题清单可取（F-WRT-03）' (Success $scenario) ("主题数 " + $scenarioCount)

# ------------------------------------------------------------------ 8. 语法域
Write-Output ''
Write-Output '[8] 语法域：语境库（F-GRM-05）与趣味玩法（F-GRM-04）'

# 8.1 语境库：与背单词域读的是**同一张关联表、同一批例句**
$ctx = Invoke-Json -Method 'GET' -Path ('/api/v1/study/grammar/context?wordCode=' + $wordCode) -Token $kidToken
$ctxItems = @()
$ctxTotal = -1
if (Success $ctx -and $ctx.result) {
    $ctxItems = @($ctx.result.items)
    $ctxTotal = $ctx.result.total
}
Check '语法语境库可取（F-GRM-05，与背单词同源）' ((Success $ctx) -and $ctxItems.Count -ge 1) `
    ("条数 " + $ctxItems.Count + ' / total ' + $ctxTotal)
Check '语境库 total 与返回条数一致（不受分页截断）' ($ctxTotal -eq $ctxItems.Count) `
    ("total=" + $ctxTotal + " items=" + $ctxItems.Count)

# 8.2 句子侦探取题：四句话，不含答案/对错字段（R-01）
$det = Invoke-Json -Method 'GET' -Path '/api/v1/study/grammar/game/detective/next' -Token $kidToken
$detRaw = if ($det.result) { $det.result | ConvertTo-Json -Depth 8 -Compress } else { '' }
$detSentences = @()
if (Success $det -and $det.result) { $detSentences = @($det.result.sentences) }
Check '句子侦探取题可取（F-GRM-04）' ((Success $det) -and $detSentences.Count -eq 4) ("句数 " + $detSentences.Count)
$detHit = @(@('"answer"', '"correctAnswer"', '"analysis"', '"wrongSentence"', '"correct"') |
    Where-Object { $detRaw -match [regex]::Escape($_) })
Check '句子侦探取题不含答案/对错字段（R-01）' ($detHit.Count -eq 0) `
    $(if ($detHit.Count -gt 0) { "命中 " + ($detHit -join ',') } else { '已隐藏' })

# 8.3 句子侦探判定：判定来源必须是规则（R-02），结果里不得有分数/名次（R-04）
$detRoundId = ''
if (Success $det) { $detRoundId = $det.result.roundId }
if ($detRoundId) {
    $detJudge = Invoke-Json -Method 'POST' -Path '/api/v1/study/grammar/game/detective/judge' -Token $kidToken `
        -Body (@{ roundId = $detRoundId; pickedIndex = 1 })
    Check '句子侦探判定可提交' (Success $detJudge) `
        $(if ($detJudge.result) { "correct=" + $detJudge.result.correct } else { $detJudge.message })
    if (Success $detJudge -and $detJudge.result) {
        Check '判定来源为规则（judge_source=rule，R-02）' ($detJudge.result.judgeSource -eq 'rule') `
            ("source=" + $detJudge.result.judgeSource)
        $judgeRaw = $detJudge.result | ConvertTo-Json -Depth 6 -Compress
        Check '判定结果不含分数/名次/百分位字段（R-04）' `
            (($judgeRaw -notmatch '"score"') -and ($judgeRaw -notmatch '"rank"') -and ($judgeRaw -notmatch '"percentile"')) ''
    }
} else {
    Check '句子侦探判定可提交' $false '未取到 roundId'
}

# 8.4 变形闯关：只给中文意思，不给目标表达（R-01）
$tr = Invoke-Json -Method 'GET' -Path '/api/v1/study/grammar/game/transform/next' -Token $kidToken
$trRaw = if ($tr.result) { $tr.result | ConvertTo-Json -Depth 8 -Compress } else { '' }
Check '变形闯关取题可取' (Success $tr) $(if ($tr.result) { $tr.result.meaning } else { $tr.message })
Check '变形闯关取题不给目标表达（R-01）' `
    (($trRaw -notmatch '"referenceText"') -and ($trRaw -notmatch '"target"') -and (Success $tr)) ''

# 8.5 错句收藏馆与闯关进度
$coll = Invoke-Json -Method 'GET' -Path '/api/v1/study/grammar/game/collection' -Token $kidToken
$collCount = 0
if (Success $coll -and $coll.result) { $collCount = @($coll.result.items).Count }
Check '错句收藏馆可取（F-GRM-06 读统一错题池）' (Success $coll) ("条数 " + $collCount)
$prog = Invoke-Json -Method 'GET' -Path '/api/v1/study/grammar/game/progress' -Token $kidToken
$progRaw = if ($prog.result) { $prog.result | ConvertTo-Json -Depth 8 -Compress } else { '' }
Check '闯关进度可取' (Success $prog) ''
Check '闯关进度不含分数/名次/百分位字段（R-04）' `
    (($progRaw -notmatch '"score"') -and ($progRaw -notmatch '"rank"') -and ($progRaw -notmatch '"percentile"')) ''

# 8.6 家长角色打语法学习端 → 权限层拦下（家长不持有 study:kid:grammar:*）
$parentGrammar = Invoke-Json -Method 'GET' -Path ('/api/v1/study/grammar/context?wordCode=' + $wordCode) -Token $parentToken
Check '家长打语法学习端被权限层拦下' (-not (Success $parentGrammar)) `
    $(if ($parentGrammar.message) { $parentGrammar.message } else { '已拒绝' })

# ------------------------------------------------------------------ 9. 越权分两层
Write-Output ''
Write-Output '[9] 越权：权限拒绝 与 数据域 4010'
# 8.1 权限层：家长角色没有孩子端权限码 → 底座 Shiro 拦下（不是 4010）
$crossPerm = Invoke-Json -Method 'GET' -Path '/api/v1/study/practice/math/next' -Token $parentToken
Check '家长打孩子端接口被权限层拦下' (-not (Success $crossPerm)) `
    $(if ($crossPerm.message) { $crossPerm.message } else { '已拒绝' })

# 8.2 数据域层：家长访问「存在但与自己无关」的孩子 → 必须 4010（不是 404）
$crossScope = Invoke-Json -Method 'GET' -Path ('/api/v1/study/error-pool?childCode=' + $OtherChildCode) -Token $parentToken
$crossCode = ''
if ($crossScope.code) { $crossCode = $crossScope.code }
Check '家长访问他人孩子的数据被拒' (-not (Success $crossScope)) `
    $(if ($crossScope.message) { $crossScope.message } else { '已拒绝' })
Check '数据域越权错误码为 4010（不用 404，避免资源探测）' ("$crossCode" -eq '4010') ("code=" + $crossCode)

# 8.3 不存在的 childCode 也必须 4010（与「存在但无权」响应形态一致）
$missing = Invoke-Json -Method 'GET' -Path '/api/v1/study/error-pool?childCode=CHD-999999' -Token $parentToken
$missingCode = ''
if ($missing.code) { $missingCode = $missing.code }
Check '不存在的 childCode 同样返 4010（不暴露资源是否存在）' ("$missingCode" -eq '4010') ("code=" + $missingCode)

$noToken = Invoke-Json -Method 'GET' -Path '/api/v1/study/practice/math/next'
Check '无 token 访问学习端被拒' (-not (Success $noToken)) ''

# ------------------------------------------------------------------ 汇总
Write-Output ''
Write-Output '==================== 汇总 ===================='
Write-Output ("通过 {0}，失败 {1}" -f $script:Pass, $script:Fail)
if ($script:Fail -gt 0) {
    Write-Output '失败用例：'
    foreach ($f in $script:Failed) { Write-Output ("  - {0}" -f $f) }
    exit 2
}
Write-Output 'ALL PASS'
exit 0
