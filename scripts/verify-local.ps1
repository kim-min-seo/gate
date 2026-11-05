param([string]$BaseUrl='http://localhost:8080')
$ErrorActionPreference='Stop'
function Assert-Gate($Condition,[string]$Message){if(!$Condition){throw $Message}}
function Get-Csrf([string]$Html){
    $token=[regex]::Match($Html,'name="_csrf"[^>]*value="([^"]+)"').Groups[1].Value
    Assert-Gate (![string]::IsNullOrEmpty($token)) 'CSRF token missing'
    return [uri]::EscapeDataString($token)
}
$signup=Invoke-WebRequest "$BaseUrl/signup" -SessionVariable session
$email='verify-'+[guid]::NewGuid().ToString('N')+'@example.invalid'
$password=[guid]::NewGuid().ToString('N')
$token=Get-Csrf $signup.Content
$result=Invoke-WebRequest "$BaseUrl/signup" -Method Post -WebSession $session -ContentType 'application/x-www-form-urlencoded' -Body "email=$email&password=$password&_csrf=$token"
Assert-Gate ($result.StatusCode -eq 200) 'Signup failed'
Write-Output 'PASS signup with CSRF'
$date=(Get-Date).AddDays(1).ToString('yyyy-MM-dd')
$slots=Invoke-WebRequest "$BaseUrl/slots?date=$date" -WebSession $session
$seatPath=[regex]::Match($slots.Content,'href="(/slots/(\d+)/seats)"')
Assert-Gate $seatPath.Success 'No future slots'
$slotId=$seatPath.Groups[2].Value
$path=$seatPath.Groups[1].Value
$page=Invoke-WebRequest "$BaseUrl$path" -WebSession $session
Assert-Gate (([regex]::Matches($page.Content,'name="seatIds"')).Count -eq 100) 'Expected 100 seats'
Write-Output 'PASS date and 100-seat page'
$token=Get-Csrf $page.Content
$auto=Invoke-WebRequest "$BaseUrl/reservations/auto" -Method Post -WebSession $session -ContentType 'application/x-www-form-urlencoded' -Body "slotId=$slotId&count=2&_csrf=$token"
Assert-Gate ($auto.Content -match 'id="pay-button"') 'Automatic assignment did not reach checkout'
Assert-Gate ($auto.Content -match '20000') 'Unexpected checkout amount'
Assert-Gate ($auto.Content -notmatch 'ANONYMOUS') 'Expected rendered customer identifier'
Write-Output 'PASS automatic assignment of 2 seats reaches checkout'
$page=Invoke-WebRequest "$BaseUrl$path" -WebSession $session
$ids=@([regex]::Matches($page.Content,'<input[^>]*name="seatIds"[^>]*>') | Where-Object {$_.Value -notmatch 'disabled'} | Select-Object -First 2 | ForEach-Object {[regex]::Match($_.Value,'value="(\d+)"').Groups[1].Value})
Assert-Gate ($ids.Count -eq 2) 'Not enough available seats'
$token=Get-Csrf $page.Content
$checkout=Invoke-WebRequest "$BaseUrl/payment" -Method Post -WebSession $session -ContentType 'application/x-www-form-urlencoded' -Body "slotId=$slotId&seatIds=$($ids[0])&seatIds=$($ids[1])&_csrf=$token"
Assert-Gate ($checkout.Content -match 'id="pay-button"') 'Manual checkout failed'
Assert-Gate ($checkout.Content -match 'const orderId = "GATE-') 'Order number not rendered'
Write-Output 'PASS manual selection of 2 seats reaches checkout'
$blocked=Invoke-WebRequest "$BaseUrl/reservations/cancel" -Method Post -Body @{reservationId=1} -WebSession $session -SkipHttpErrorCheck
Assert-Gate ($blocked.StatusCode -eq 403) 'Cancellation without CSRF accepted'
Write-Output 'PASS missing CSRF rejected'
for($order=0;$order -lt 2;$order++){
    $mine=Invoke-WebRequest "$BaseUrl/my-reservations" -WebSession $session
    $reservationId=[regex]::Match($mine.Content,'name="reservationId"[^>]*value="(\d+)"').Groups[1].Value
    Assert-Gate (![string]::IsNullOrEmpty($reservationId)) 'Active order missing'
    $token=Get-Csrf $mine.Content
    $cancel=Invoke-WebRequest "$BaseUrl/reservations/cancel" -Method Post -WebSession $session -ContentType 'application/x-www-form-urlencoded' -Body "reservationId=$reservationId&_csrf=$token"
    Assert-Gate ($cancel.StatusCode -eq 200) 'Cancellation page failed'
}
$mine=Invoke-WebRequest "$BaseUrl/my-reservations" -WebSession $session
Assert-Gate ($mine.Content -notmatch 'name="reservationId"') 'Some reservations remain active'
$page=Invoke-WebRequest "$BaseUrl$path" -WebSession $session
foreach($id in $ids){
    $input=[regex]::Match($page.Content,"<input[^>]*name=`"seatIds`"[^>]*value=`"$id`"[^>]*>").Value
    Assert-Gate ($input -and $input -notmatch 'disabled') 'Cancelled seat not returned'
}
Write-Output 'PASS both whole orders cancelled and selected seats released'
$token=Get-Csrf $mine.Content
$logout=Invoke-WebRequest "$BaseUrl/logout" -Method Post -WebSession $session -ContentType 'application/x-www-form-urlencoded' -Body "_csrf=$token"
Assert-Gate ($logout.Content -match 'action="/login"') 'Logout did not reach login form'
$token=Get-Csrf $logout.Content
$login=Invoke-WebRequest "$BaseUrl/login" -Method Post -WebSession $session -ContentType 'application/x-www-form-urlencoded' -Body "email=$email&password=$password&_csrf=$token"
$mine=Invoke-WebRequest "$BaseUrl/my-reservations" -WebSession $session
Assert-Gate ($mine.Content -match 'MY RESERVATIONS') 'Login persistence failed'
Write-Output 'PASS logout and login again'
Write-Output 'No payment provider confirmation was performed. Synthetic account and cancelled reservations are retained.'
