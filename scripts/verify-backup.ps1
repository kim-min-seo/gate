param([Parameter(Mandatory=$true)][string]$BackupPath)
$ErrorActionPreference='Stop'
$backup=(Resolve-Path -LiteralPath $BackupPath).Path
$metadataPath=$backup+'.json'
if(!(Test-Path -LiteralPath $metadataPath)){throw 'Backup checksum metadata is missing'}
$metadata=Get-Content -Raw -LiteralPath $metadataPath | ConvertFrom-Json
if((Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -ne $metadata.Sha256){throw 'Backup checksum mismatch'}
$name='gate-restore-check-'+[guid]::NewGuid().ToString('N')
$password=[guid]::NewGuid().ToString('N')
$containerId=$null
try {
    # No host ports, bind mounts or existing volumes: the source database is never a restore target.
    $containerId=(& docker run --detach --rm --name $name -e "POSTGRES_PASSWORD=$password" -e POSTGRES_DB=gate postgres:17-alpine)
    if($LASTEXITCODE -ne 0){throw 'Could not start isolated restore container'}
    $ready=$false
    for($i=0;$i -lt 30;$i++){
        & docker exec $containerId pg_isready -h 127.0.0.1 -U postgres -d gate *> $null
        if($LASTEXITCODE -eq 0){$ready=$true;break}
        Start-Sleep -Seconds 1
    }
    if(!$ready){throw 'Restore database did not become ready'}
    & docker cp $backup "${containerId}:/tmp/gate.dump"
    if($LASTEXITCODE -ne 0){throw 'Archive copy failed'}
    & docker exec $containerId pg_restore -h 127.0.0.1 --exit-on-error --no-owner --no-privileges -U postgres -d gate /tmp/gate.dump
    if($LASTEXITCODE -ne 0){throw 'Restore failed'}
    $sql="select 'users', count(*) from users union all select 'slot',count(*) from slot union all select 'seat',count(*) from seat union all select 'reservation',count(*) from reservation union all select 'payment',count(*) from payment union all select 'payment_recovery',count(*) from payment_recovery;"
    & docker exec $containerId psql -h 127.0.0.1 -v ON_ERROR_STOP=1 -U postgres -d gate -c $sql
    if($LASTEXITCODE -ne 0){throw 'Restored schema verification failed'}
    Write-Output 'PASS checksum, isolated PostgreSQL restore and application table queries'
} finally {
    if($containerId){
        # Only stop the exact temporary container created by this run.
        & docker stop $containerId | Out-Null
    }
}
