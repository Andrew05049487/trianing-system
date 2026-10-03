param([string]$BaseUrl = 'https://trianing-system-1.onrender.com')
$ErrorActionPreference = 'Stop'
if ($BaseUrl -ne 'https://trianing-system-1.onrender.com') { throw 'Unexpected service; refusing to transmit test credentials.' }
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$logins = Import-Clixml -LiteralPath (Join-Path $repo '.local/research-demo-logins.credential.xml')
function Login([string]$kind) {
    $credential = $logins[$kind]
    $body = @{ identifier=($credential.UserName + '@demo.invalid'); password=$credential.GetNetworkCredential().Password } | ConvertTo-Json
    $result = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 45
    if (!$result.customExerciseToken) { throw 'Authenticated identity missing.' }
    return @{ 'X-User-Id'=[string]$result.userId; 'X-Custom-Exercise-Token'=$result.customExerciseToken }
}
$patient = Login 'PATIENT'; $therapist = Login 'THERAPIST'; $reviewer = Login 'REVIEWER'; $manager = Login 'MANAGER'
$consent = Invoke-RestMethod "$BaseUrl/api/ml-research/consent" -Headers $patient -TimeoutSec 45
if (!$consent.available -or !$consent.handAvailable -or $consent.currentVersion -ne 'hand-research-consent-v1') { throw 'Actual runtime not ready for hand consent.' }
Write-Output 'PASS: actual Render consent available=true and handAvailable=true.'
$body = @{agree=$true;version=$consent.currentVersion} | ConvertTo-Json
$accepted = Invoke-RestMethod "$BaseUrl/api/ml-research/consent" -Headers $patient -Method Put -ContentType 'application/json' -Body $body -TimeoutSec 45
if (!$accepted.active) { throw 'Consent not persisted.' }
$list = Invoke-RestMethod "$BaseUrl/api/ml-research/samples" -Headers $patient -TimeoutSec 45
$sample = @($list.content | Where-Object clientSampleId -eq 'DEMO-HAND-001')
if ($sample.Count -ne 1) { throw 'Expected exactly one DEMO-HAND-001.' }
$detail = Invoke-RestMethod "$BaseUrl/api/ml-research/samples/$($sample[0].id)" -Headers $patient -TimeoutSec 45
if ($detail.payload.frames.Count -ne 41 -or @($detail.payload.frames | Where-Object { $_.landmarks.Count -ne 21 }).Count -ne 0) { throw 'Invalid hand sequence.' }
$payload = $detail.payload
$payload.sampleId = 'DEMO-HAND-001'
$json = $payload | ConvertTo-Json -Depth 30 -Compress
$retry = Invoke-RestMethod "$BaseUrl/api/ml-research/samples" -Headers $patient -Method Post -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes($json)) -TimeoutSec 45
if ($retry.id -ne $sample[0].id) { throw 'Duplicate upload did not reuse sample.' }
$visible = Invoke-RestMethod "$BaseUrl/api/ml-research/samples/$($retry.id)" -Headers $therapist -TimeoutSec 45
if ($visible.payload.frames[0].landmarks.Count -ne 21) { throw 'Therapist cannot read 21 landmarks.' }
Write-Output 'PASS: real HTTP patient consent/upload/dedup and therapist full21-point read.'

# Separate named synthetic sample exercises review without changing the user's initially unlabeled001.
$payload.sampleId = 'DEMO-HAND-003'
$json = $payload | ConvertTo-Json -Depth 30 -Compress
$e2e = Invoke-RestMethod "$BaseUrl/api/ml-research/samples" -Headers $patient -Method Post -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes($json)) -TimeoutSec 45
$e2eDetail = Invoke-RestMethod "$BaseUrl/api/ml-research/samples/$($e2e.id)" -Headers $therapist -TimeoutSec 45
if ($e2eDetail.annotation.status -ne 'APPROVED') {
    if ($e2eDetail.annotation.status -ne 'SUBMITTED') {
        $label = @{label='meets_requirement';note='DEMO synthetic UI/API fixture; not clinical evidence';labelVersion='hand-research-v1';actionDefinitionVersion='sidePinch-hand-v1'} | ConvertTo-Json
        $draft = Invoke-RestMethod "$BaseUrl/api/ml-research/samples/$($e2e.id)/label" -Headers $therapist -Method Put -ContentType 'application/json' -Body $label -TimeoutSec 45
        if ($draft.status -ne 'DRAFT') { throw 'Draft not saved.' }
        $submitted = Invoke-RestMethod "$BaseUrl/api/ml-research/samples/$($e2e.id)/label/submit" -Headers $therapist -Method Post -TimeoutSec 45
        if ($submitted.status -ne 'SUBMITTED') { throw 'Submission failed.' }
    }
    $review = @{approve=$true;note='DEMO independent review only; not IRB or model approval'} | ConvertTo-Json
    $self=Invoke-WebRequest "$BaseUrl/api/ml-research/samples/$($e2e.id)/label/review" -Headers $therapist -Method Post -ContentType 'application/json' -Body $review -TimeoutSec 45 -SkipHttpErrorCheck
    if ($self.StatusCode -ne 403) {throw 'Self review must be forbidden.'}
    Write-Output 'PASS: author with review authority still cannot approve own annotation (403).'
    $approved = Invoke-RestMethod "$BaseUrl/api/ml-research/samples/$($e2e.id)/label/review" -Headers $reviewer -Method Post -ContentType 'application/json' -Body $review -TimeoutSec 45
    if ($approved.status -ne 'APPROVED') { throw 'Independent review failed.' }
}
Write-Output 'PASS: real Render annotation/submit/independent review, using synthetic003 only.'
$first = Invoke-RestMethod "$BaseUrl/api/ml-research/samples/$($retry.id)" -Headers $therapist -TimeoutSec 45
if ($first.annotation) { throw '001 should remain initially unlabeled for manual acceptance.' }
Write-Output 'PASS: DEMO-HAND-001 remains unlabeled; no fake model result.'
