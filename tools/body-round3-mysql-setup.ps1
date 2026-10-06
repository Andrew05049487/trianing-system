param()
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$mysql = 'C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe'
$schema = 'rehab_body_r3_validation'
# Run manually in a local terminal. Password stays in mysql's interactive prompt.
# Never touch the old rehab_r2_validation or any production schema.
$probe = @(& $mysql --no-defaults --no-login-paths --protocol=TCP --host=127.0.0.1 --port=3306 --user=root --password --batch --skip-column-names --execute="SELECT VERSION(); SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$schema';")
if ($LASTEXITCODE -ne 0 -or $probe.Count -ne 2 -or $probe[0] -ne '8.4.11' -or $probe[1] -ne '0') {
    throw 'Unexpected server, authentication failure or target schema already exists. Nothing changed.'
}
$migrationDir = (Join-Path $repo 'docs/database-rebuild/mysql').Replace('\','/')
$files = @('V001__main_schema.sql','V002__research_schema.sql','V003__custom_pose_rules.sql','V004__body_attempt_research.sql')
foreach ($file in $files) { if (-not (Test-Path -LiteralPath "$migrationDir/$file")) { throw "Missing migration: $file" } }
$commands = "CREATE DATABASE $schema CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs; USE $schema; "
foreach ($file in $files) { $commands += "`n" + (Get-Content -LiteralPath "$migrationDir/$file" -Raw -Encoding UTF8) + "`n" }
$commands += "GRANT SELECT,INSERT,UPDATE,DELETE ON $schema.* TO 'rehab_app'@'localhost'; SHOW TABLES;"
# Batch input without --force stops on the first SQL error; no SOURCE continuation.
$OutputEncoding = [Text.UTF8Encoding]::new($false)
$commands | & $mysql --no-defaults --no-login-paths --protocol=TCP --host=127.0.0.1 --port=3306 --user=root --password --default-character-set=utf8mb4 --batch
if ($LASTEXITCODE -ne 0) { throw 'Migration failed. Preserve error and partially created isolated schema; do not rerun blindly.' }
Write-Output 'Isolated schema migrated; application receives only schema CRUD. No production data or collection enabled.'
