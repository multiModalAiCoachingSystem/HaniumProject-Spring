[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$DumpFile,
    [string]$ChecksumFile,
    [string]$EnvFile,
    [string]$TargetContainer,
    [string]$TargetDatabase,
    [bool]$CreatePreRestoreBackup = $true,
    [string]$BackupRoot,
    [switch]$ConfirmRestore,
    [string]$ConfirmationText
)

. (Join-Path $PSScriptRoot "Backup.Common.ps1")

$projectRoot = Get-ProjectRoot
$composePaths = Get-ComposePaths -ProjectRoot $projectRoot
if ([string]::IsNullOrWhiteSpace($EnvFile)) { $EnvFile = $composePaths.EnvFile }
$dump = Get-Item -LiteralPath $DumpFile -ErrorAction Stop
if ($dump.Length -le 0 -or -not (Select-String -LiteralPath $dump.FullName -SimpleMatch "CREATE TABLE" -Quiet)) {
    throw "The selected dump is empty or does not contain schema statements."
}

if ([string]::IsNullOrWhiteSpace($ChecksumFile)) {
    $candidate = $dump.FullName + ".sha256"
    if (Test-Path -LiteralPath $candidate) { $ChecksumFile = $candidate }
}
if (-not [string]::IsNullOrWhiteSpace($ChecksumFile)) {
    Test-Sha256File -FilePath $dump.FullName -ChecksumPath $ChecksumFile
}

if ([string]::IsNullOrWhiteSpace($TargetContainer)) {
    $TargetContainer = Get-MySqlContainerId -ComposeFile $composePaths.ComposeFile -EnvFile $EnvFile
}
if ([string]::IsNullOrWhiteSpace($TargetDatabase)) {
    $TargetDatabase = Get-ContainerDatabaseName -ContainerId $TargetContainer
}
if ($TargetDatabase -notmatch '^[A-Za-z0-9_]+$') { throw "Unsafe target database name." }

$requiredConfirmation = "RESTORE $TargetDatabase"
if (-not $ConfirmRestore) {
    $ConfirmationText = Read-Host "Type '$requiredConfirmation' to restore into container '$TargetContainer'"
}
if ($ConfirmationText -ne $requiredConfirmation) {
    throw "Restore confirmation did not match. No database changes were made."
}

if ($CreatePreRestoreBackup) {
    if ([string]::IsNullOrWhiteSpace($BackupRoot)) {
        $BackupRoot = Resolve-BackupRoot -BackupRoot $BackupRoot -EnvFile $EnvFile
    }
    & (Join-Path $PSScriptRoot "backup-mysql.ps1") `
        -BackupRoot $BackupRoot `
        -EnvFile $EnvFile `
        -TargetContainer $TargetContainer `
        -TargetDatabase $TargetDatabase | Out-Host
}

$containerDump = "/tmp/mulmoco-restore-$([Guid]::NewGuid().ToString('N')).sql"
try {
    & docker.exe cp $dump.FullName "${TargetContainer}:$containerDump"
    if ($LASTEXITCODE -ne 0) { throw "Could not copy the dump into the target container." }

    & docker.exe exec -e "DUMP_PATH=$containerDump" -e "TARGET_DATABASE=$TargetDatabase" $TargetContainer sh -lc @'
set -eu
export MYSQL_PWD="$MYSQL_PASSWORD"
mysql --user="$MYSQL_USER" "$TARGET_DATABASE" < "$DUMP_PATH"
'@
    if ($LASTEXITCODE -ne 0) { throw "MySQL restore failed." }

    $tables = Invoke-MySqlScalar -ContainerId $TargetContainer -Database $TargetDatabase `
        -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE();"
    $coreRows = Invoke-MySqlScalar -ContainerId $TargetContainer -Database $TargetDatabase `
        -Sql "SELECT CONCAT((SELECT COUNT(*) FROM users),',',(SELECT COUNT(*) FROM uploaded_videos),',',(SELECT COUNT(*) FROM analysis_jobs),',',(SELECT COUNT(*) FROM final_analysis_results));"
    Write-Host "MySQL restore completed. tableCount=$tables coreRowCounts=$coreRows"
} finally {
    & docker.exe exec -e "DUMP_PATH=$containerDump" $TargetContainer sh -lc 'rm -f -- "$DUMP_PATH"' 2>$null | Out-Null
}
