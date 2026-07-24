[CmdletBinding()]
param(
    [string]$BackupRoot,
    [string]$EnvFile,
    [string]$StorageRoot,
    [Nullable[bool]]$IncludeTempArtifacts,
    [int]$RetentionDays,
    [int]$KeepMinimum,
    [switch]$AllowActiveJobs
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

$containerId = Get-MySqlContainerId -ComposeFile $composePaths.ComposeFile -EnvFile $EnvFile
$activeCount = [int](Invoke-MySqlScalar -ContainerId $containerId `
    -Sql "SELECT COUNT(*) FROM analysis_jobs WHERE status IN ('PROCESSING','CANCEL_REQUESTED');")
if ($activeCount -gt 0 -and -not $AllowActiveJobs) {
    throw "There are $activeCount active analysis jobs. Retry after they finish or pass -AllowActiveJobs explicitly."
}
if ($activeCount -gt 0) {
    Write-Warning "Backing up while $activeCount analysis jobs are active can produce storage/DB timing differences."
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$setsRoot = Join-Path $root "sets"
New-Item -ItemType Directory -Path $setsRoot -Force | Out-Null
$partialSet = Assert-PathWithinRoot -Path (Join-Path $setsRoot "$timestamp.partial") -Root $root
$finalSet = Assert-PathWithinRoot -Path (Join-Path $setsRoot $timestamp) -Root $root
New-Item -ItemType Directory -Path $partialSet -Force | Out-Null

try {
    $databaseFile = (& (Join-Path $PSScriptRoot "backup-mysql.ps1") `
        -BackupRoot $root -EnvFile $EnvFile -OutputDirectory $partialSet -SkipRetention -SkipChecksum |
        Select-Object -Last 1)
    $storageParameters = @{
        BackupRoot = $root
        EnvFile = $EnvFile
        OutputDirectory = $partialSet
        SkipRetention = $true
        SkipChecksum = $true
    }
    if (-not [string]::IsNullOrWhiteSpace($StorageRoot)) { $storageParameters.StorageRoot = $StorageRoot }
    if ($null -ne $IncludeTempArtifacts) { $storageParameters.IncludeTempArtifacts = $IncludeTempArtifacts }
    $storageFile = (& (Join-Path $PSScriptRoot "backup-storage.ps1") @storageParameters | Select-Object -Last 1)

    $databaseItem = Get-Item -LiteralPath $databaseFile
    $storageItem = Get-Item -LiteralPath $storageFile
    $hasFlywayHistory = Invoke-MySqlScalar -ContainerId $containerId `
        -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'flyway_schema_history';"
    $schemaVersion = if ($hasFlywayHistory -eq "1") {
        Invoke-MySqlScalar -ContainerId $containerId `
            -Sql "SELECT COALESCE(MAX(version),'baseline') FROM flyway_schema_history WHERE success = 1;"
    } else {
        "unmanaged"
    }
    $applicationVersion = (& git.exe -C $projectRoot rev-parse --short=12 HEAD 2>$null).Trim()
    if ([string]::IsNullOrWhiteSpace($applicationVersion)) { $applicationVersion = "unknown" }

    $manifest = [ordered]@{
        createdAt = [DateTimeOffset]::Now.ToString("o")
        databaseFile = $databaseItem.Name
        storageFile = $storageItem.Name
        databaseSize = $databaseItem.Length
        storageSize = $storageItem.Length
        applicationVersion = $applicationVersion
        flywayVersion = "11.20.2"
        schemaVersion = $schemaVersion
        activeAnalysisJobsAtStart = $activeCount
        includesTemporaryArtifacts = [bool]$IncludeTempArtifacts
    }
    $manifestPath = Join-Path $partialSet "manifest.json"
    $manifest | ConvertTo-Json | Set-Content -LiteralPath $manifestPath -Encoding UTF8

    $checksumLines = @()
    foreach ($file in @($databaseItem, $storageItem, (Get-Item $manifestPath))) {
        $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $checksumLines += "$hash *$($file.Name)"
    }
    $checksumPath = Join-Path $partialSet "checksums.sha256"
    Set-Content -LiteralPath $checksumPath -Encoding ASCII -Value $checksumLines
    if ((Get-Item $checksumPath).Length -le 0) { throw "Checksum manifest creation failed." }

    Move-Item -LiteralPath $partialSet -Destination $finalSet
    Write-Host "Integrated backup completed: $finalSet"

    $sets = @(Get-ChildItem -LiteralPath $setsRoot -Directory | Where-Object Name -Match '^\d{8}_\d{6}$')
    Remove-ExpiredBackupItems -Root $root -Items $sets -RetentionDays $RetentionDays -KeepMinimum $KeepMinimum
    Write-Output $finalSet
} catch {
    if (Test-Path -LiteralPath $partialSet) {
        $safePartial = Assert-PathWithinRoot -Path $partialSet -Root $root
        Remove-Item -LiteralPath $safePartial -Recurse -Force
    }
    throw
}
