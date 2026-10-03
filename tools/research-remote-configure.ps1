$ErrorActionPreference='Stop'
$base='https://trianing-system-1.onrender.com'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$credentials=Import-Clixml -LiteralPath (Join-Path $repo '.local/research-demo-logins.credential.xml')
function Login($kind) {
    $c=$credentials[$kind]
    $body=@{identifier=($c.UserName+'@demo.invalid');password=$c.GetNetworkCredential().Password}|ConvertTo-Json
    $u=Invoke-RestMethod "$base/api/auth/login" -Method Post -ContentType application/json -Body $body -TimeoutSec 45
    if ($u.email -ne ($c.UserName+'@demo.invalid') -or !$u.name.StartsWith('DEMO')) {throw 'Fake identity mismatch.'}
    return $u
}
function Headers($user) {return @{'X-User-Id'=[string]$user.userId;'X-Custom-Exercise-Token'=$user.customExerciseToken}}
$manager=Login 'MANAGER';$mh=Headers $manager
$authority=Invoke-RestMethod "$base/api/ml-research/authority/me" -Headers $mh -TimeoutSec 45
if (!$authority.canManage) {throw 'Existing authenticated manager authorization required; no escalation performed.'}
$policyResponse=Invoke-RestMethod "$base/api/ml-research/management/retention" -Headers $mh -TimeoutSec 45
$policies=@($policyResponse | ForEach-Object {$_})
$before=@{service=$base;managerId=$manager.userId;policies=$policies;recordedAt=[DateTime]::UtcNow.ToString('o')}
$snapshot=Join-Path $repo '.local/render-research-policy-before.json'
if (!(Test-Path -LiteralPath $snapshot)) {$before|ConvertTo-Json -Depth 10|Set-Content -LiteralPath $snapshot -Encoding utf8}
$current=@($policies|Where-Object { [DateTime]$_.effectiveAt -le [DateTime]::UtcNow }|Sort-Object effectiveAt -Descending)
if (!$current.Count) {
    if (@($policies|Where-Object policyVersion -eq 'hand-retention-v1').Count) {throw 'Future existing policy must not be overwritten.'}
    $body=@{policyVersion='hand-retention-v1';retentionDays=90;effectiveAt=[DateTime]::UtcNow.AddSeconds(-1).ToString('o');approvalReference='OWNER-AUTH-20261003-G5'}|ConvertTo-Json
    Invoke-RestMethod "$base/api/ml-research/management/retention" -Headers $mh -Method Post -ContentType application/json -Body $body -TimeoutSec 45|Out-Null
    Write-Output 'PASS: actual Render policy created (90 days; internal owner reference, not IRB).'
} else {Write-Output 'PASS: preserved existing effective policy, no duplicate/overwrite.'}
$patient=Login 'PATIENT';$ph=Headers $patient
foreach($kind in @('THERAPIST','REVIEWER')) {
    $u=Login $kind;$h=Headers $u
    $beforeGrant=Invoke-RestMethod "$base/api/ml-research/authority/me" -Headers $h -TimeoutSec 45
    if (!$beforeGrant.canReview -or ($kind -eq 'THERAPIST' -and !$beforeGrant.canAnnotate)) {
        $body=@{canAnnotate=($kind -eq 'THERAPIST');canReview=$true;canManage=$false}|ConvertTo-Json
        Invoke-RestMethod "$base/api/ml-research/authority/grants/$($u.userId)" -Headers $mh -Method Put -ContentType application/json -Body $body -TimeoutSec 45|Out-Null
    }
}
$consent=Invoke-RestMethod "$base/api/ml-research/consent" -Headers $ph -TimeoutSec 45
if (!$consent.available -or !$consent.handAvailable) {throw 'Actual service not available after policy setup.'}
$body=@{agree=$true;version=$consent.currentVersion}|ConvertTo-Json
Invoke-RestMethod "$base/api/ml-research/consent" -Headers $ph -Method Put -ContentType application/json -Body $body -TimeoutSec 45|Out-Null
# Only this explicitly synthetic patient is opted in here. Ordinary patients choose in the App.
$list=Invoke-RestMethod "$base/api/ml-research/samples" -Headers $ph -TimeoutSec 45
if (!@($list.content|Where-Object clientSampleId -eq 'DEMO-HAND-001').Count) {
    $db=Import-Clixml -LiteralPath (Join-Path $repo '.local/mysql-app.credential.xml')
    $env:MYSQL_PWD=$db.GetNetworkCredential().Password
    try {
        $rows=@(& 'C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe' --no-defaults --no-login-paths --protocol=TCP --host=127.0.0.1 --user=$($db.UserName) --database=rehab_r2_validation --default-character-set=utf8mb4 --batch --raw --skip-column-names --execute="SELECT s.payload_json FROM research_samples s JOIN users u ON u.id=s.participant_user_id WHERE s.client_sample_id='DEMO-HAND-001' AND u.email='demo_patient@demo.invalid';")
        if ($LASTEXITCODE -ne 0 -or $rows.Count -ne 1) {throw 'Expected one validated synthetic local fixture; no other payload read.'}
        $payload=$rows[0]|ConvertFrom-Json;$payload.sampleId='DEMO-HAND-001'
        $json=$payload|ConvertTo-Json -Depth 30 -Compress
        Invoke-RestMethod "$base/api/ml-research/samples" -Headers $ph -Method Post -ContentType application/json -Body ([Text.Encoding]::UTF8.GetBytes($json)) -TimeoutSec 45|Out-Null
    } finally {Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue}
}
Write-Output 'PASS: actual Render policy/grants/consent and DEMO-HAND-001 prepared through existing APIs.'
