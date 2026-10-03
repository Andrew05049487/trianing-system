param([ValidateSet('DEMO-HAND-001','DEMO-HAND-002','DEMO-HAND-003')][string]$SampleName='DEMO-HAND-001')
$ErrorActionPreference='Stop'
$base='https://trianing-system-1.onrender.com'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$c=(Import-Clixml (Join-Path $repo '.local/research-demo-logins.credential.xml'))['PATIENT']
$login=@{identifier='demo_patient@demo.invalid';password=$c.GetNetworkCredential().Password}|ConvertTo-Json
$u=Invoke-RestMethod "$base/api/auth/login" -Method Post -ContentType application/json -Body $login -TimeoutSec 45
if ($u.userId -ne 253 -or $u.email -ne 'demo_patient@demo.invalid' -or $u.role -ne 'PATIENT' -or !$u.name.StartsWith('DEMO')) {throw 'Explicit synthetic identity mismatch; no deletion.'}
$h=@{'X-User-Id'=[string]$u.userId;'X-Custom-Exercise-Token'=$u.customExerciseToken}
$list=Invoke-RestMethod "$base/api/ml-research/samples" -Headers $h -TimeoutSec 45
$rows=@($list.content|Where-Object clientSampleId -eq $SampleName)
if ($rows.Count -ne 1) {throw 'Expected one explicitly named DEMO row; no deletion.'}
$detail=Invoke-RestMethod "$base/api/ml-research/samples/$($rows[0].id)" -Headers $h -TimeoutSec 45
if ($detail.sample.clientSampleId -ne $SampleName -or $detail.payload.actionId -ne 'sidePinch' -or $detail.payload.frames.Count -ne 41) {throw 'Not the verified synthetic fixture; no deletion.'}
# Snapshot contains ONLY the authorized synthetic row, never unrelated research or tokens.
$snapshot=Join-Path $repo ('.local/remote-reset-'+$SampleName+'-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfff')+'.json')
$detail|ConvertTo-Json -Depth 35|Set-Content -LiteralPath $snapshot -Encoding utf8
$payload=$detail.payload;$payload.sampleId=$SampleName
$json=$payload|ConvertTo-Json -Depth 30 -Compress
Invoke-RestMethod "$base/api/ml-research/samples/$($rows[0].id)" -Headers $h -Method Delete -TimeoutSec 45|Out-Null
try {
    $new=Invoke-RestMethod "$base/api/ml-research/samples" -Headers $h -Method Post -ContentType application/json -Body ([Text.Encoding]::UTF8.GetBytes($json)) -TimeoutSec 45
    if ($new.annotationStatus -ne 'UNLABELED') {throw 'Unexpected new annotation status.'}
    Write-Output ('PASS: '+$SampleName+' reset to UNLABELED using original APIs; server ID='+$new.id)
} catch {
    throw 'Re-upload failed after synthetic deletion. Snapshot retained in ignored .local/remote-reset-*; restore ONLY its payload with original upload API. No other rows affected.'
}
# Sample audit/deletion records remain. This is not a purge of governance history.
