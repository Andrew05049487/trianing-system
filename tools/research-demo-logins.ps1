# Displays ONLY generated disposable DEMO credentials when explicitly invoked by the operator.
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$logins=Import-Clixml (Join-Path $repo '.local/research-demo-logins.credential.xml')
foreach ($kind in @('PATIENT','THERAPIST','REVIEWER','MANAGER')) {
    $c=$logins[$kind]
    if ($c.UserName -ne ('demo_'+$kind.ToLower())) {throw 'Unexpected credential; refusing to display.'}
    [pscustomobject]@{Role=$kind;Email=($c.UserName+'@demo.invalid');Password=$c.GetNetworkCredential().Password}
}
