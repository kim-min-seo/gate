param([string]$BaseUrl='http://localhost:8080',[string]$RedisContainer='gate-redis-1')
$ErrorActionPreference='Stop'
$page=Invoke-WebRequest "$BaseUrl/signup" -SessionVariable session
$token=[regex]::Match($page.Content,'name="_csrf"[^>]*value="([^"]+)"').Groups[1].Value
$email='expired-form-'+[guid]::NewGuid().ToString('N')+'@example.invalid';$password=[guid]::NewGuid().ToString('N')
$result=Invoke-WebRequest "$BaseUrl/signup" -Method Post -WebSession $session -Body @{email=$email;password=$password;_csrf=$token}
$mine=Invoke-WebRequest "$BaseUrl/my-reservations" -WebSession $session
$oldToken=[regex]::Match($mine.Content,'name="_csrf"[^>]*value="([^"]+)"').Groups[1].Value
$cookie=$session.Cookies.GetCookies([uri]$BaseUrl)|Where-Object Name -eq 'GATE_SESSION'
if(!$cookie){throw 'Test session cookie missing'}
$id=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($cookie.Value))
if($id -notmatch '^[0-9a-f-]{36}$'){throw 'Unexpected test session ID format'}
# Invalidate only this newly created synthetic session, never all Redis data.
$removed=& docker exec $RedisContainer redis-cli DEL "gate:session:sessions:$id"
if($LASTEXITCODE -ne 0 -or $removed -ne '1'){throw 'Could not invalidate synthetic session'}
$expired=Invoke-WebRequest "$BaseUrl/reservations/cancel" -Method Post -WebSession $session -Body @{reservationId=1;_csrf=$oldToken}
if($expired.Content -notmatch 'action="/login"' -or $expired.Content -notmatch '로그인 시간이 만료'){throw 'Stale form did not reach expiry guidance'}
Write-Output 'PASS expired-session form redirects to login guidance; no cancellation executed'
$api=Invoke-WebRequest "$BaseUrl/api/example" -Method Post -WebSession $session -SkipHttpErrorCheck
if($api.StatusCode -ne 403){throw 'API CSRF error must remain 403'}
Write-Output 'PASS API invalid CSRF remains HTTP 403'
