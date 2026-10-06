param([ValidateSet('Setup','Cleanup','Status')][string]$Mode='Status')
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$schema='rehab_body_r3_validation'
$base='http://127.0.0.1:18083'
$mysql='C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe'
$stateFile=Join-Path $repo '.local/body-r3-device.credential.xml'
$credential=Import-Clixml (Join-Path $repo '.local/mysql-app.credential.xml')
$previous=[Environment]::GetEnvironmentVariable('MYSQL_PWD','Process')
function Sql([string]$statement) {
    $result=@(& $mysql --no-defaults --no-login-paths --protocol=TCP --host=127.0.0.1 "--user=$($credential.UserName)" --database=$schema --default-character-set=utf8mb4 --batch --skip-column-names --execute=$statement)
    if($LASTEXITCODE -ne 0) {throw 'Isolated fixture SQL failed; no broader cleanup attempted.'}
    return ,$result
}
function Api([string]$path,[string]$method='GET',$body=$null,$headers=@{}) {
    $args=@{Uri="$base$path";Method=$method;Headers=$headers;TimeoutSec=30}
    if($null -ne $body) {$args.ContentType='application/json';$args.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 30 -Compress))}
    return Invoke-RestMethod @args
}
function Login($entry) {
    $login=Api '/api/auth/login' 'POST' @{identifier=$entry.Credential.UserName;password=$entry.Credential.GetNetworkCredential().Password}
    if(!$login.customExerciseToken -or [long]$login.userId -ne [long]$entry.Id) {throw 'Fixture identity mismatch.'}
    return @{'X-User-Id'=[string]$login.userId;'X-Custom-Exercise-Token'=$login.customExerciseToken}
}
try {
    $env:MYSQL_PWD=$credential.GetNetworkCredential().Password
    $probe=Sql 'SELECT VERSION(); SELECT DATABASE();'
    if($probe.Count -ne 2 -or $probe[0] -ne '8.4.11' -or $probe[1] -ne $schema) {throw 'Not the exact local validation schema.'}
    if($Mode -eq 'Status') {
        Sql "SELECT 'users',COUNT(*) FROM users UNION ALL SELECT 'samples',COUNT(*) FROM research_samples UNION ALL SELECT 'policies',COUNT(*) FROM research_retention_policies;"
        exit
    }
    if($Mode -eq 'Setup') {
        if(Test-Path $stateFile) {
            $prior=Import-Clixml $stateFile
            if($prior.Schema -ne $schema -or !$prior.Cleaned) {
                throw 'Active fixture checkpoint exists. Use Status/Cleanup rather than duplicating fixtures.'
            }
        }
        if([int](Sql 'SELECT COUNT(*) FROM users;')[0] -ne 0 -or [int](Sql 'SELECT COUNT(*) FROM research_retention_policies;')[0] -ne 0) {throw 'Unexpected preexisting data. No fixtures written.'}
        $tag=[Guid]::NewGuid().ToString('N').Substring(0,12)
        $state=@{Schema=$schema;Tag=$tag;Accounts=@{};ExerciseId=$null;PolicyVersion="r3-device-$tag";Cleaned=$false}
        $state | Export-Clixml $stateFile
        foreach($kind in @('PATIENT','AUTHOR','REVIEWER','MANAGER')) {
            $email="r3-$($kind.ToLower())-$tag@example.invalid"
            $password=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(16))
            $cred=[pscredential]::new($email,(ConvertTo-SecureString $password -AsPlainText -Force))
            $null=Api $(if($kind -eq 'PATIENT'){'/api/auth/register'}else{'/api/auth/therapist/register'}) 'POST' @{name="ROUND3 SYNTHETIC $kind";email=$email;password=$password}
            $login=Api '/api/auth/login' 'POST' @{identifier=$email;password=$password}
            $state.Accounts[$kind]=@{Id=[long]$login.userId;Credential=$cred;BindingCode=$login.bindingCode}
            $state | Export-Clixml $stateFile
        }
        $patient=$state.Accounts.PATIENT;$manager=$state.Accounts.MANAGER
        $null=Sql "INSERT INTO research_grants(user_id,study_id,can_annotate,can_review,can_manage,updated_at) VALUES($($manager.Id),'standing-knee-raise-v1',0,0,1,UTC_TIMESTAMP(6)); INSERT INTO research_grant_audit(study_id,target_user_id,action,created_at) VALUES('standing-knee-raise-v1',$($manager.Id),'ROUND3_SYNTHETIC_BOOTSTRAP',UTC_TIMESTAMP(6));"
        $managerHeaders=Login $manager
        foreach($kind in @('AUTHOR','REVIEWER')) {
            $entry=$state.Accounts[$kind];$headers=Login $entry
            $null=Api '/api/therapist/patients/bind' 'POST' @{bindingCode=$patient.BindingCode} $headers
            $null=Api "/api/ml-research/authority/grants/$($entry.Id)" 'PUT' @{canAnnotate=$true;canReview=$true;canManage=$false} $managerHeaders
        }
        $null=Api '/api/ml-research/management/retention' 'POST' @{policyVersion=$state.PolicyVersion;retentionDays=1;effectiveAt=[DateTime]::UtcNow.AddSeconds(-1).ToString('o');approvalReference='SYNTHETIC-TEST-NOT-APPROVAL'} $managerHeaders
        $state.ExerciseId=[long](Sql "INSERT INTO exercise(exercise_name,description) VALUES('站姿抬腳式訓練','ROUND3 SYNTHETIC $tag'); SELECT LAST_INSERT_ID();")[0]
        $state | Export-Clixml $stateFile
        $null=Api "/api/assignable-exercises/DEFAULT/$($state.ExerciseId)/patients/$($patient.Id)" 'PUT' @{} (Login $state.Accounts.AUTHOR)
        $consent=Api '/api/ml-research/consent' 'GET' $null (Login $patient)
        if(!$consent.available -or $consent.active) {throw 'Expected available but NOT automatically consented.'}
        Write-Output 'PASS: fake accounts, binding, grants, test-only retention, real DEFAULT assignment; consent available and still opt-in.'
        Write-Output 'Passwords stored only via Windows DPAPI in ignored .local. No synthetic samples seeded; device must collect them.'
        exit
    }
    $state=Import-Clixml $stateFile
    if($state.Schema -ne $schema -or $state.Cleaned) {throw 'No active exact fixture manifest.'}
    $ids=@($state.Accounts.Values | ForEach-Object {[long]$_.Id})
    if($ids.Count -lt 1) {throw 'No captured fixture IDs.'}
    foreach($entry in $state.Accounts.Values) {
        $email=$entry.Credential.UserName
        if($email -notmatch '^r3-(patient|author|reviewer|manager)-[a-f0-9]{12}@example\.invalid$') {throw 'Invalid synthetic email guard.'}
        if([int](Sql "SELECT COUNT(*) FROM users WHERE id=$($entry.Id) AND email='$email';")[0] -ne 1) {throw 'Account cleanup identity mismatch.'}
    }
    $in=$ids -join ','
    $cleanup=@"
START TRANSACTION;
DELETE r FROM research_annotation_revisions r JOIN research_samples s ON s.id=r.sample_id WHERE s.participant_user_id IN ($in);
DELETE a FROM research_annotations a JOIN research_samples s ON s.id=a.sample_id WHERE s.participant_user_id IN ($in);
DELETE FROM research_retention_events WHERE actor_user_id IN ($in) OR sample_id IN (SELECT id FROM research_samples WHERE participant_user_id IN ($in));
DELETE FROM research_audit WHERE actor_user_id IN ($in);
DELETE FROM research_samples WHERE participant_user_id IN ($in);
DELETE FROM research_consents WHERE user_id IN ($in);
DELETE FROM research_export_audit WHERE actor_user_id IN ($in);
DELETE FROM research_grant_audit WHERE actor_user_id IN ($in) OR target_user_id IN ($in);
DELETE FROM research_review_requests WHERE user_id IN ($in);
DELETE FROM research_grants WHERE user_id IN ($in);
DELETE FROM research_retention_policies WHERE configured_by_user_id IN ($in) AND policy_version='$($state.PolicyVersion)';
DELETE FROM training_history WHERE user_id IN ($in);
DELETE FROM training_session_results WHERE patient_id IN ($in);
DELETE FROM exercise_result WHERE user_id IN ($in);
DELETE FROM exercise_assignments WHERE patient_id IN ($in);
DELETE FROM user_bindings WHERE patient_id IN ($in) OR linked_user_id IN ($in);
DELETE FROM users WHERE id IN ($in);
"@
    if($state.ExerciseId) {$cleanup+="DELETE FROM exercise WHERE id=$($state.ExerciseId) AND description='ROUND3 SYNTHETIC $($state.Tag)';"}
    $cleanup+='COMMIT;'
    $null=Sql $cleanup
    $state.Cleaned=$true;$state | Export-Clixml $stateFile
    Write-Output 'PASS: exact synthetic fixture cleanup committed; no table clear or migration replay.'
} finally {[Environment]::SetEnvironmentVariable('MYSQL_PWD',$previous,'Process')}
