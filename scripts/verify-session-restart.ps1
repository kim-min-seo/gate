param(
    [ValidateSet('BeforeRestart','AfterRestart')][string]$Phase,
    [string]$BaseUrl='http://localhost:8080',
    [string]$StatePath=(Join-Path $PSScriptRoot '../backups/session-restart-probe.clixml')
)
$ErrorActionPreference='Stop'
if($Phase -eq 'BeforeRestart'){
    $page=Invoke-WebRequest "$BaseUrl/signup" -SessionVariable session
    $token=[regex]::Match($page.Content,'name="_csrf"[^>]*value="([^"]+)"').Groups[1].Value
    $email='session-check-'+[guid]::NewGuid().ToString('N')+'@example.invalid'
    $password=[guid]::NewGuid().ToString('N')
    $result=Invoke-WebRequest "$BaseUrl/signup" -Method Post -WebSession $session -Body @{email=$email;password=$password;_csrf=$token}
    $mine=Invoke-WebRequest "$BaseUrl/my-reservations" -WebSession $session
    if($mine.Content -notmatch 'MY RESERVATIONS'){throw 'Login failed'}
    $cookie=$session.Cookies.GetCookies([uri]$BaseUrl)|Where-Object Name -eq 'GATE_SESSION'
    if(!$cookie){throw 'Gate-specific cookie missing'}
    New-Item -ItemType Directory -Force (Split-Path $StatePath) | Out-Null
    [pscustomobject]@{Name=$cookie.Name;Value=$cookie.Value;Email=$email}|Export-Clixml -LiteralPath $StatePath
    Write-Output 'PASS Gate session cookie and authenticated request before restart'
} else {
    $state=Import-Clixml -LiteralPath $StatePath
    $session=[Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $session.Cookies.Add([uri]$BaseUrl,[System.Net.Cookie]::new($state.Name,$state.Value,'/'))
    $mine=Invoke-WebRequest "$BaseUrl/my-reservations" -WebSession $session
    if($mine.Content -notmatch 'MY RESERVATIONS'){throw 'Login did not survive application restart'}
    Write-Output 'PASS same login remains authenticated after application restart'
    Remove-Item -LiteralPath $StatePath
}
