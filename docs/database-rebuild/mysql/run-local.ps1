param([ValidateSet('Setup','Test','FullTest','Compile','Package','Start','Seed','Metadata')][string]$Action = 'Test')
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Set-Location -LiteralPath $repo
$credentialPath = Join-Path $repo '.local/mysql-app.credential.xml'
if ($Action -eq 'Setup') {
    if (Test-Path -LiteralPath $credentialPath) { throw 'Local credential already exists; do not overwrite.' }
    # The root password is entered only at mysql's interactive prompt.
    # Generated application password is captured, never printed, and DPAPI-encrypted.
    $mysql = 'C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe'
    $probe = @(& $mysql --no-defaults --no-login-paths --protocol=TCP --host=127.0.0.1 --port=3306 --user=root --password --batch --skip-column-names --execute="SELECT VERSION(); SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='rehab_r2_validation' AND TABLE_TYPE='BASE TABLE';")
    if ($LASTEXITCODE -ne 0 -or $probe.Count -ne 2 -or $probe[0] -ne '8.4.11' -or $probe[1] -ne '29') { throw 'Unexpected server/schema; application user was not created.' }
    Write-Output 'Verified MySQL 8.4.11 / 29-table isolated schema. Enter root credential again to provision the restricted application user.'
    $sql = "CREATE USER 'rehab_app'@'localhost' IDENTIFIED BY RANDOM PASSWORD; GRANT SELECT,INSERT,UPDATE,DELETE ON rehab_r2_validation.* TO 'rehab_app'@'localhost';"
    $result = @($sql | & 'C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe' --no-defaults --no-login-paths --protocol=TCP --host=127.0.0.1 --port=3306 --user=root --password --batch --raw)
    if ($LASTEXITCODE -ne 0) { throw 'MySQL setup failed; do not retry blindly.' }
    $passwordRow = @($result | Where-Object { $_ -match '^rehab_app\tlocalhost\t' })
    if ($passwordRow.Count -ne 1) { throw 'Generated credential result missing.' }
    $secure = ConvertTo-SecureString ($passwordRow[0].Split("`t")[2]) -AsPlainText -Force
    New-Item -ItemType Directory -Path (Join-Path $repo '.local') -Force | Out-Null
    [pscredential]::new('rehab_app',$secure) | Export-Clixml -LiteralPath $credentialPath
    $result = $null; $passwordRow = $null
    Write-Output 'Application user created; schema-only CRUD; local credential protected with Windows DPAPI and ignored by Git.'
    exit
}
$credential = Import-Clixml -LiteralPath $credentialPath
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/rehab_r2_validation?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&useServerPrepStmts=true&characterEncoding=UTF-8'
$env:DB_USERNAME = $credential.UserName
$env:DB_PASSWORD = $credential.GetNetworkCredential().Password
$env:RESEARCH_COLLECTION_ENABLED = 'false'
# Local-only ephemeral HMAC secrets; never affect production or persist in Git.
$env:CUSTOM_EXERCISE_IDENTITY_SECRET = [guid]::NewGuid().ToString() + [guid]::NewGuid().ToString()
$env:PASSWORD_RESET_SECRET = [guid]::NewGuid().ToString() + [guid]::NewGuid().ToString()
try {
    switch ($Action) {
        'Test' { & mvn -q '-Dtest=MySqlMigrationIntegrationTest' test }
        'FullTest' { & mvn test }
        'Seed' {
            $env:MYSQL_PWD = $credential.GetNetworkCredential().Password
            Get-Content -Raw -Encoding UTF8 (Join-Path $PSScriptRoot 'seed_default_exercises.sql') | & 'C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe' --no-defaults --no-login-paths --protocol=TCP --host=127.0.0.1 --user=rehab_app --database=rehab_r2_validation --default-character-set=utf8mb4 --batch
        }
        'Metadata' {
            $env:MYSQL_PWD = $credential.GetNetworkCredential().Password
            # Extend the immutable R2 validator's manifest in memory for V003.
            $sql = Get-Content -Raw -Encoding UTF8 (Join-Path $PSScriptRoot 'validate_schema.sql')
            $entry = '{"t":"custom_rehab_exercises","s":1,"n":"pose_measurement_rules_json","typ":"longtext","nullable":"YES","d":null,"auto":0,"gen":"","storage":"","coll":"utf8mb4_0900_as_cs"}'
            $sql = $sql.Replace("SET @r2_columns='[", "SET @r2_columns='[$entry,")
            "SET @r2_include_research=1;`n$sql`nSHOW TABLES;`nSELECT COUNT(*) AS columns_actual FROM information_schema.columns WHERE table_schema=DATABASE();`nSELECT COUNT(*) AS fk_actual FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE();`nSELECT COUNT(*) AS checks_actual FROM information_schema.check_constraints WHERE constraint_schema=DATABASE();`nSELECT COUNT(*) AS indexes_actual FROM (SELECT table_name,index_name FROM information_schema.statistics WHERE table_schema=DATABASE() GROUP BY table_name,index_name) x;" | & 'C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe' --no-defaults --no-login-paths --protocol=TCP --host=127.0.0.1 --user=rehab_app --database=rehab_r2_validation --batch
        }
        'Compile' { & mvn -q -DskipTests compile }
        'Package' { & mvn -q -DskipTests package }
        'Start' { & mvn -q spring-boot:run '-Dspring-boot.run.arguments=--server.port=18083' }
    }
    exit $LASTEXITCODE
} finally {
    Remove-Item Env:DB_PASSWORD,Env:CUSTOM_EXERCISE_IDENTITY_SECRET,Env:PASSWORD_RESET_SECRET,Env:MYSQL_PWD -ErrorAction SilentlyContinue
}
