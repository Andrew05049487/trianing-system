$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$schema='rehab_body_r3_validation'
$mysql='C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe'
$credential=Import-Clixml -LiteralPath (Join-Path $repo '.local/mysql-app.credential.xml')
$names=@('MYSQL_PWD','DB_URL','DB_USERNAME','DB_PASSWORD','CUSTOM_EXERCISE_IDENTITY_SECRET','PASSWORD_RESET_SECRET','RESEARCH_COLLECTION_ENABLED','JAVA_HOME')
$previous=@{}
foreach($name in $names) { $previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process') }
try {
    $env:MYSQL_PWD=$credential.GetNetworkCredential().Password
    $probe=@(& $mysql --no-defaults --no-login-paths --protocol=TCP --host=127.0.0.1 "--user=$($credential.UserName)" --database=$schema --batch --skip-column-names --execute="SELECT VERSION(); SELECT DATABASE(); SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND ((TABLE_NAME='research_samples' AND COLUMN_NAME='resample_of_sample_id') OR (TABLE_NAME='research_annotations' AND COLUMN_NAME='reason_code') OR (TABLE_NAME='research_annotation_revisions' AND COLUMN_NAME IN ('reason_code','disposition')));")
    if($LASTEXITCODE -ne 0 -or $probe.Count -ne 3 -or $probe[0] -ne '8.4.11' -or $probe[1] -ne $schema -or $probe[2] -ne '4') {
        throw 'Local isolated MySQL/V005 preflight failed. No tests or migrations executed.'
    }
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    $env:DB_URL='jdbc:mysql://127.0.0.1:3306/rehab_body_r3_validation?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
    $env:DB_USERNAME=$credential.UserName
    $env:DB_PASSWORD=$credential.GetNetworkCredential().Password
    $env:CUSTOM_EXERCISE_IDENTITY_SECRET=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
    $env:PASSWORD_RESET_SECRET=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
    $env:RESEARCH_COLLECTION_ENABLED='false'
    $env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
    Push-Location $repo
    try { & 'C:/Program Files/apache-maven-3.9.16/bin/mvn.cmd' '-Dtest=BodyRound3MySqlIntegrationTest' test
        if($LASTEXITCODE -ne 0) { throw 'Isolated MySQL integration tests failed. See Surefire reports.' }
    } finally { Pop-Location }
} finally {
    foreach($name in $names) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
}
