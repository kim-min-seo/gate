param([string]$BaseUrl='http://localhost:8080')
$ErrorActionPreference='Stop'
function Assert-Check($condition,$message){if(!$condition){throw $message}}
function Token($html){[uri]::EscapeDataString([regex]::Match($html,'name="_csrf"[^>]*value="([^"]+)"').Groups[1].Value)}
$page=Invoke-WebRequest "$BaseUrl/signup" -SessionVariable session
$email='reselect-'+[guid]::NewGuid().ToString('N')+'@example.invalid';$password=[guid]::NewGuid().ToString('N')
$result=Invoke-WebRequest "$BaseUrl/signup" -Method Post -WebSession $session -ContentType 'application/x-www-form-urlencoded' -Body "email=$email&password=$password&_csrf=$(Token $page.Content)"
$date=(Get-Date).AddDays(1).ToString('yyyy-MM-dd')
$page=Invoke-WebRequest "$BaseUrl/slots?date=$date" -WebSession $session
$match=[regex]::Match($page.Content,'href="(/slots/(\d+)/seats)"');$path=$match.Groups[1].Value;$slot=$match.Groups[2].Value
for($attempt=1;$attempt -le 2;$attempt++){
    $page=Invoke-WebRequest "$BaseUrl$path" -WebSession $session
    $checkout=Invoke-WebRequest "$BaseUrl/reservations/auto" -Method Post -WebSession $session -ContentType 'application/x-www-form-urlencoded' -Body "slotId=$slot&count=4&_csrf=$(Token $page.Content)"
    Assert-Check ($checkout.Content -match 'id="pay-button"' -and $checkout.Content -match '40000') 'Four-seat auto checkout failed'
    $mine=Invoke-WebRequest "$BaseUrl/my-reservations" -WebSession $session
    Assert-Check (([regex]::Matches($mine.Content,'name="reservationId"')).Count -eq 4) 'Active holds accumulated'
    Write-Output "PASS auto checkout attempt ${attempt}: exactly four active seats"
}
$page=Invoke-WebRequest "$BaseUrl$path" -WebSession $session
$selectable=@([regex]::Matches($page.Content,'<input[^>]*name="seatIds"[^>]*>')|Where-Object {$_.Value -notmatch 'disabled'}|Select-Object -First 4|ForEach-Object {[regex]::Match($_.Value,'value="(\d+)"').Groups[1].Value})
Assert-Check ($selectable.Count -eq 4) 'Cannot select seats after returning from checkout'
$body="slotId=$slot&_csrf=$(Token $page.Content)";foreach($id in $selectable){$body+='&seatIds='+$id}
$checkout=Invoke-WebRequest "$BaseUrl/payment" -Method Post -WebSession $session -ContentType 'application/x-www-form-urlencoded' -Body $body
Assert-Check ($checkout.Content -match 'id="pay-button"') 'Manual reselection checkout failed'
$mine=Invoke-WebRequest "$BaseUrl/my-reservations" -WebSession $session
Assert-Check (([regex]::Matches($mine.Content,'name="reservationId"')).Count -eq 4) 'Manual checkout exceeded four active seats'
Write-Output 'PASS auto four seats then manual four seats without hold accumulation'
$id=[regex]::Match($mine.Content,'name="reservationId"[^>]*value="(\d+)"').Groups[1].Value
$cancel=Invoke-WebRequest "$BaseUrl/reservations/cancel" -Method Post -WebSession $session -ContentType 'application/x-www-form-urlencoded' -Body "reservationId=$id&_csrf=$(Token $mine.Content)"
Assert-Check ($cancel.Content -notmatch 'name="reservationId"') 'Cleanup cancellation failed'
Write-Output 'PASS whole-order cancellation and cleanup'
Write-Output 'Provider authentication was not performed. Synthetic account and cancelled history are retained.'
