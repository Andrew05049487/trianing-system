$ErrorActionPreference = 'Stop'
$baseUrl = 'https://trianing-system-1.onrender.com'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$logins = Import-Clixml -LiteralPath (Join-Path $repo '.local/research-demo-logins.credential.xml')
foreach ($kind in @('PATIENT','THERAPIST','REVIEWER','MANAGER')) {
    $credential = $logins[$kind]
    $email = $credential.UserName + '@demo.invalid'
    $password = $credential.GetNetworkCredential().Password
    $loginBody = @{identifier=$email;password=$password} | ConvertTo-Json
    $user = $null
    try { $user = Invoke-RestMethod "$baseUrl/api/auth/login" -Method Post -ContentType 'application/json' -Body $loginBody -TimeoutSec 45 }
    catch { if ([int]$_.Exception.Response.StatusCode -ne 401) { throw } }
    if (!$user) {
        # Only named fictitious addresses. An existing conflicting account is never reset/overwritten.
        $path = if ($kind -eq 'PATIENT') { '/api/auth/register' } else { '/api/auth/therapist/register' }
        $register = @{email=$email;password=$password;name="DEMO $kind"} | ConvertTo-Json
        Invoke-RestMethod "$baseUrl$path" -Method Post -ContentType 'application/json' -Body $register -TimeoutSec 45 | Out-Null
        $user = Invoke-RestMethod "$baseUrl/api/auth/login" -Method Post -ContentType 'application/json' -Body $loginBody -TimeoutSec 45
    }
    if ($user.email -ne $email -or !$user.name.StartsWith('DEMO') -or !$user.customExerciseToken) { throw 'Fake account identity mismatch.' }
    $expectedRole = if ($kind -eq 'PATIENT') { 'PATIENT' } else { 'THERAPIST' }
    if ($user.role -ne $expectedRole) { throw 'Fake account role mismatch.' }
    $headers = @{'X-User-Id'=[string]$user.userId;'X-Custom-Exercise-Token'=$user.customExerciseToken}
    $authority = Invoke-RestMethod "$baseUrl/api/ml-research/authority/me" -Headers $headers -TimeoutSec 45
    Write-Output "$kind : id=$($user.userId), email=$email, role=$($user.role), canManage=$($authority.canManage), canAnnotate=$($authority.canAnnotate), canReview=$($authority.canReview)"
}
