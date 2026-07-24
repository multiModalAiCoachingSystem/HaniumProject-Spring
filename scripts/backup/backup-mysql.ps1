[CmdletBinding()]
param(
    [string]$BackupRoot,
    [string]$EnvFile,
    [string]$OutputDirectory,
    [string]$TargetContainer,
    [string]$TargetDatabase,
    [int]$RetentionDays,
    [int]$KeepMinimum,
    [switch]$SkipRetention,
    [switch]$SkipChecksum
)

. (Join-Path $PSScriptRoot "Backup.Common.ps1")

$projectRoot = Get-ProjectRoot
$composePaths = Get-ComposePaths -ProjectRoot $projectRoot
if ([string]::IsNullOrWhiteSpace($EnvFile)) { $EnvFile = $composePaths.EnvFile }
$root = Resolve-BackupRoot -BackupRoot $BackupRoot -EnvFile $EnvFile

if ($RetentionDays -le 0) {
    $value = Get-EnvValue -EnvFile $EnvFile -Name "BACKUP_RETENTION_DAYS"
    $RetentionDays = if ($value) { [int]$value } else { 14 }
}
if ($KeepMinimum -le 0) {
    $value = Get-EnvValue -EnvFile $EnvFile -Name "BACKUP_KEEP_MINIMUM"
    $KeepMinimum = if ($value) { [int]$value } else { 3 }
}

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $root "database"
}
$OutputDirectory = Assert-PathWithinRoot -Path $OutputDirectory -Root $root
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null

if ([string]::IsNullOrWhiteSpace($TargetContainer)) {
    $containerId = Get-MySqlContainerId -ComposeFile $composePaths.ComposeFile -EnvFile $EnvFile
} else {
    $containerId = $TargetContainer
}
if ([string]::IsNullOrWhiteSpace($TargetDatabase)) {
    $database = Get-ContainerDatabaseName -ContainerId $containerId
} else {
    if ($TargetDatabase -notmatch '^[A-Za-z0-9_]+$') { throw "Unsafe target database name." }
    $database = $TargetDatabase
}
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$finalPath = Join-Path $OutputDirectory "${database}_${timestamp}.sql"
$partialPath = $finalPath + ".partial"
$containerDump = "/tmp/mulmoco-backup-$([Guid]::NewGuid().ToString('N')).sql"

try {
    & docker.exe exec -e "DUMP_PATH=$containerDump" -e "TARGET_DATABASE=$database" $containerId sh -lc @'
set -eu
export MYSQL_PWD="$MYSQL_PASSWORD"
mysqldump \
  --user="$MYSQL_USER" \
  --single-transaction \
  --quick \
  --routines \
  --events \
  --triggers \
  --hex-blob \
  --set-gtid-purged=OFF \
  --no-tablespaces \
  --default-character-set=utf8mb4 \
  "$TARGET_DATABASE" > "$DUMP_PATH"
test -s "$DUMP_PATH"
'@
    if ($LASTEXITCODE -ne 0) { throw "mysqldump failed." }

    & docker.exe cp "${containerId}:$containerDump" $partialPath
    if ($LASTEXITCODE -ne 0) { throw "Could not copy the dump from the mysql container." }

    $dump = Get-Item -LiteralPath $partialPath
    if ($dump.Length -le 0) { throw "The database dump is empty." }
    if (-not (Select-String -LiteralPath $partialPath -SimpleMatch "CREATE TABLE" -Quiet)) {
        throw "The database dump does not contain expected schema statements."
    }

    Move-Item -LiteralPath $partialPath -Destination $finalPath
    if (-not $SkipChecksum) {
        $checksum = Write-Sha256File -FilePath $finalPath
        Write-Host "Checksum created: $checksum"
    }
    Write-Host "MySQL backup completed: $finalPath"

    if (-not $SkipRetention) {
        $items = @(Get-ChildItem -LiteralPath $OutputDirectory -File -Filter "${database}_*.sql")
        Remove-ExpiredBackupItems -Root $root -Items $items -RetentionDays $RetentionDays -KeepMinimum $KeepMinimum
    }
    Write-Output $finalPath
} catch {
    if (Test-Path -LiteralPath $partialPath) {
        $safePartial = Assert-PathWithinRoot -Path $partialPath -Root $root
        Remove-Item -LiteralPath $safePartial -Force
    }
    throw
} finally {
    & docker.exe exec -e "DUMP_PATH=$containerDump" $containerId sh -lc 'rm -f -- "$DUMP_PATH"' 2>$null | Out-Null
}
