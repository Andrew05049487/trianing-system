param([ValidateSet('Seed','Reset')][string]$Action = 'Seed')
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location -LiteralPath $repo
$credential = Import-Clixml -LiteralPath (Join-Path $repo '.local/mysql-app.credential.xml')
$loginPath = Join-Path $repo '.local/research-demo-logins.credential.xml'
if (!(Test-Path -LiteralPath $loginPath)) {
    $logins = @{}
    foreach ($kind in @('PATIENT','THERAPIST','REVIEWER','MANAGER')) {
        $logins[$kind] = [pscredential]::new("demo_$($kind.ToLower())", (ConvertTo-SecureString ('Demo!' + [guid]::NewGuid().ToString('N')) -AsPlainText -Force))
    }
    $logins | Export-Clixml -LiteralPath $loginPath
}
$logins = Import-Clixml -LiteralPath $loginPath
$env:MYSQL_PWD = $credential.GetNetworkCredential().Password
try {
    $mysql = 'C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe'
    # Before writes: only policy metadata/counts and named synthetic records, no private users.
    $snapshot = @(& $mysql --no-defaults --no-login-paths --protocol=TCP --host=127.0.0.1 --port=3306 --user=$($credential.UserName) --database=rehab_r2_validation --batch --execute="SELECT VERSION(),DATABASE(); SELECT study_id,policy_version,retention_days,effective_at,approval_reference FROM research_retention_policies; SELECT id,account_id,role FROM users WHERE account_id IN ('demo_patient','demo_therapist','demo_reviewer','demo_manager'); SELECT id,client_sample_id FROM research_samples WHERE client_sample_id='DEMO-HAND-001';")
    if ($LASTEXITCODE -ne 0) { throw 'Snapshot failed; no setup attempted.' }
    $snapshot | Set-Content -LiteralPath (Join-Path $repo ('.local/research-before-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfff') + '.txt')) -Encoding utf8
    $env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/rehab_r2_validation?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&useServerPrepStmts=true&characterEncoding=UTF-8'
    $env:DB_USERNAME = $credential.UserName
    $env:DB_PASSWORD = $credential.GetNetworkCredential().Password
    $env:RESEARCH_ACTIVATION_SETUP = 'true'
    $env:RESEARCH_SETUP_ACTION = $Action.ToLower()
    $env:CUSTOM_EXERCISE_IDENTITY_SECRET = [guid]::NewGuid().ToString() + [guid]::NewGuid().ToString()
    $env:PASSWORD_RESET_SECRET = [guid]::NewGuid().ToString() + [guid]::NewGuid().ToString()
    foreach ($kind in @('PATIENT','THERAPIST','REVIEWER','MANAGER')) {
        [Environment]::SetEnvironmentVariable("DEMO_$($kind)_PASSWORD", $logins[$kind].GetNetworkCredential().Password, 'Process')
    }
    & mvn -q '-Dtest=ResearchActivationSetupTest' test
    if ($LASTEXITCODE -ne 0) { throw 'Setup failed; inspect transaction/error before retrying.' }
    Write-Output "Completed $Action; fake login credentials protected in .local/research-demo-logins.credential.xml."
} finally {
    foreach ($key in @('MYSQL_PWD','DB_PASSWORD','CUSTOM_EXERCISE_IDENTITY_SECRET','PASSWORD_RESET_SECRET','RESEARCH_ACTIVATION_SETUP','RESEARCH_SETUP_ACTION','DEMO_PATIENT_PASSWORD','DEMO_THERAPIST_PASSWORD','DEMO_REVIEWER_PASSWORD','DEMO_MANAGER_PASSWORD')) {
        [Environment]::SetEnvironmentVariable($key, $null, 'Process')
    }
}
