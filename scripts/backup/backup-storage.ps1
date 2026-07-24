[CmdletBinding()]
param(
    [string]$BackupRoot,
    [string]$EnvFile,
    [string]$StorageRoot,
    [string]$OutputDirectory,
    [Nullable[bool]]$IncludeTempArtifacts,
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

if ([string]::IsNullOrWhiteSpace($StorageRoot)) {
    $StorageRoot = Get-EnvValue -EnvFile $EnvFile -Name "HOST_STORAGE_PATH"
}
if ([string]::IsNullOrWhiteSpace($StorageRoot)) { throw "HOST_STORAGE_PATH or -StorageRoot is required." }
$StorageRoot = [System.IO.Path]::GetFullPath($StorageRoot)
if (-not (Test-Path -LiteralPath $StorageRoot -PathType Container)) {
    throw "Storage root does not exist: $StorageRoot"
}
if ($root.StartsWith($StorageRoot.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw "BACKUP_ROOT must not be inside HOST_STORAGE_PATH."
}

if ($null -eq $IncludeTempArtifacts) {
    $setting = Get-EnvValue -EnvFile $EnvFile -Name "BACKUP_INCLUDE_TEMP_ARTIFACTS"
    $IncludeTempArtifacts = "true".Equals($setting, [StringComparison]::OrdinalIgnoreCase)
}
if ($RetentionDays -le 0) {
    $value = Get-EnvValue -EnvFile $EnvFile -Name "BACKUP_RETENTION_DAYS"
    $RetentionDays = if ($value) { [int]$value } else { 14 }
}
if ($KeepMinimum -le 0) {
    $value = Get-EnvValue -EnvFile $EnvFile -Name "BACKUP_KEEP_MINIMUM"
    $KeepMinimum = if ($value) { [int]$value } else { 3 }
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $root "storage"
}
$OutputDirectory = Assert-PathWithinRoot -Path $OutputDirectory -Root $root
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null

$includeNames = @("uploads", "streams", "analysis", "thumbnails", "results")
if ($IncludeTempArtifacts) {
    $includeNames += @("video-llm", "tmp")
}
$existingNames = @($includeNames | Where-Object {
    Test-Path -LiteralPath (Join-Path $StorageRoot $_) -PathType Container
})
if ($existingNames.Count -eq 0) {
    throw "No configured storage directories exist under HOST_STORAGE_PATH."
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$finalPath = Join-Path $OutputDirectory "storage_${timestamp}.zip"
$partialPath = Join-Path $OutputDirectory "storage_${timestamp}.partial.zip"
try {
    & tar.exe -a -cf $partialPath -C $StorageRoot @existingNames
    if ($LASTEXITCODE -ne 0) { throw "Storage archive creation failed." }
    $archive = Get-Item -LiteralPath $partialPath
    if ($archive.Length -le 0) { throw "The storage archive is empty." }

    $entries = @(& tar.exe -tf $partialPath)
    if ($LASTEXITCODE -ne 0 -or $entries.Count -eq 0) {
        throw "The storage archive could not be opened or has no entries."
    }
    Move-Item -LiteralPath $partialPath -Destination $finalPath
    if (-not $SkipChecksum) {
        $checksum = Write-Sha256File -FilePath $finalPath
        Write-Host "Checksum created: $checksum"
    }
    Write-Host "Storage backup completed: $finalPath"

    if (-not $SkipRetention) {
        $items = @(Get-ChildItem -LiteralPath $OutputDirectory -File -Filter "storage_*.zip")
        Remove-ExpiredBackupItems -Root $root -Items $items -RetentionDays $RetentionDays -KeepMinimum $KeepMinimum
    }
    Write-Output $finalPath
} catch {
    if (Test-Path -LiteralPath $partialPath) {
        $safePartial = Assert-PathWithinRoot -Path $partialPath -Root $root
        Remove-Item -LiteralPath $safePartial -Force
    }
    throw
}
