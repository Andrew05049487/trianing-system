$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$mysql='C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe'
$schema='rehab_body_r3_validation'
$credential=Import-Clixml -LiteralPath (Join-Path $repo '.local/mysql-app.credential.xml')
$env:MYSQL_PWD=$credential.GetNetworkCredential().Password
try {
    $probe=@(& $mysql --no-defaults --no-login-paths --protocol=TCP --host=127.0.0.1 "--user=$($credential.UserName)" --database=$schema --batch --skip-column-names --execute="SELECT VERSION(); SELECT DATABASE(); SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND ((TABLE_NAME='research_samples' AND COLUMN_NAME='resample_of_sample_id') OR (TABLE_NAME='research_annotations' AND COLUMN_NAME='reason_code') OR (TABLE_NAME='research_annotation_revisions' AND COLUMN_NAME IN ('reason_code','disposition')));")
    if($LASTEXITCODE -ne 0 -or $probe.Count -ne 3 -or $probe[0] -ne '8.4.11' -or $probe[1] -ne $schema) { throw 'Wrong server/schema or credential error. No changes.' }
} finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
if($probe[2] -eq '4') { Write-Output 'Review metadata already exists. No migration replayed.'; exit 0 }
if($probe[2] -ne '0') { throw 'Partial migration exists. Stop and inspect; no automatic retry.' }
# Root credential only through interactive mysql prompt. The application keeps CRUD only.
$OutputEncoding=[Text.UTF8Encoding]::new($false)
Get-Content -LiteralPath (Join-Path $repo 'docs/database-rebuild/mysql/V005__body_review_resample.sql') -Raw -Encoding UTF8 |
    & $mysql --no-defaults --no-login-paths --protocol=TCP --host=127.0.0.1 --user=root --password --database=$schema --default-character-set=utf8mb4 --batch
if($LASTEXITCODE -ne 0) { throw 'V005 failed; preserve error and inspect isolated schema.' }
Write-Output 'V005 applied only to rehab_body_r3_validation; no collection or production changes.'
