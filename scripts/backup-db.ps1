param(
    [string]$Container='gate-postgres-1',
    [string]$Database='gate',
    [string]$User='gate',
    [string]$OutputDirectory=(Join-Path $PSScriptRoot '../backups')
)
$ErrorActionPreference='Stop'
if($Database -notmatch '^[A-Za-z0-9_]+$' -or $User -notmatch '^[A-Za-z0-9_]+$'){throw 'Invalid database/user identifier'}
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
$directory=(Resolve-Path $OutputDirectory).Path
$name='gate-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)+'.dump'
$destination=Join-Path $directory $name
# Copy the custom-format archive as binary; never pipe it through PowerShell text redirection.
$remote='/tmp/'+$name
& docker exec $Container pg_dump -U $User -d $Database --format=custom --no-owner --no-privileges --file=$remote
if($LASTEXITCODE -ne 0){throw 'pg_dump failed'}
& docker cp "${Container}:$remote" $destination
if($LASTEXITCODE -ne 0){throw 'Backup copy failed'}
$info=Get-Item -LiteralPath $destination
if($info.Length -eq 0){throw 'Backup archive is empty'}
$hash=(Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash
[pscustomobject]@{Backup=$destination;Bytes=$info.Length;Sha256=$hash} | ConvertTo-Json | Set-Content -Encoding utf8 ($destination+'.json')
Write-Output $destination
