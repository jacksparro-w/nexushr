param(
  [string]$OutputDirectory = "backups"
)

$ErrorActionPreference = "Stop"
$container = (docker compose ps -q postgres).Trim()
if (-not $container) { throw "PostgreSQL is not running. Start the Compose postgres service first." }

$null = New-Item -ItemType Directory -Force -Path $OutputDirectory
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$containerFile = "/tmp/nexushr-$stamp.dump"
$outputFile = Join-Path $OutputDirectory "nexushr-$stamp.dump"

docker exec $container sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f "$1"' sh $containerFile
if ($LASTEXITCODE -ne 0) { throw "PostgreSQL dump failed." }
try {
  docker cp "${container}:$containerFile" $outputFile
  if ($LASTEXITCODE -ne 0) { throw "Could not copy the backup from the database container." }
} finally {
  docker exec $container rm -f $containerFile | Out-Null
}
Write-Output "Backup created at $outputFile"
