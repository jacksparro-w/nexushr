param(
  [Parameter(Mandatory = $true)]
  [string]$BackupFile
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $BackupFile -PathType Leaf)) { throw "Backup file does not exist." }
$confirmation = Read-Host "This will replace matching objects in the current NexusHR database. Type RESTORE to continue"
if ($confirmation -cne "RESTORE") { throw "Restore cancelled." }

$container = (docker compose ps -q postgres).Trim()
if (-not $container) { throw "PostgreSQL is not running. Start the Compose postgres service first." }
$containerFile = "/tmp/nexushr-restore.dump"
$wasBackendRunning = [bool](docker compose ps --status running -q backend)
if ($wasBackendRunning) {
  docker compose stop backend | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "Could not stop the backend before restore." }
}
try {
  docker cp (Resolve-Path -LiteralPath $BackupFile).Path "${container}:$containerFile"
  if ($LASTEXITCODE -ne 0) { throw "Could not copy the backup into the database container." }
  docker exec $container sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner "$1"' sh $containerFile
  if ($LASTEXITCODE -ne 0) { throw "PostgreSQL restore failed. Review the database before restarting application services." }
} finally {
  docker exec $container rm -f $containerFile | Out-Null
  if ($wasBackendRunning) { docker compose start backend | Out-Null }
}
Write-Output "Restore completed. Verify application health and key records before serving traffic."
