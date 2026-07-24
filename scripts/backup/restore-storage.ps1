[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ArchiveFile,
    [Parameter(Mandatory = $true)][string]$TargetDirectory,
    [string]$ChecksumFile
)

. (Join-Path $PSScriptRoot "Backup.Common.ps1")

$archive = Get-Item -LiteralPath $ArchiveFile -ErrorAction Stop
if ($archive.Length -le 0) { throw "The storage archive is empty." }
if ([string]::IsNullOrWhiteSpace($ChecksumFile)) {
    $candidate = $archive.FullName + ".sha256"
    if (Test-Path -LiteralPath $candidate) { $ChecksumFile = $candidate }
}
if (-not [string]::IsNullOrWhiteSpace($ChecksumFile)) {
    Test-Sha256File -FilePath $archive.FullName -ChecksumPath $ChecksumFile
}

$entries = @(& tar.exe -tf $archive.FullName)
if ($LASTEXITCODE -ne 0 -or $entries.Count -eq 0) {
    throw "The storage archive cannot be opened."
}
foreach ($entry in $entries) {
    $normalized = $entry.Replace('\', '/')
    if ($normalized -match '(^/|^[A-Za-z]:|(^|/)\.\.(/|$))') {
        throw "The storage archive contains an unsafe path."
    }
}

$target = [System.IO.Path]::GetFullPath($TargetDirectory)
if (Test-Path -LiteralPath $target) {
    if (@(Get-ChildItem -LiteralPath $target -Force).Count -gt 0) {
        throw "TargetDirectory must be absent or empty. Existing files are never overwritten."
    }
} else {
    New-Item -ItemType Directory -Path $target -Force | Out-Null
}

& tar.exe -xf $archive.FullName -C $target
if ($LASTEXITCODE -ne 0) { throw "Storage archive extraction failed." }
$fileCount = @(Get-ChildItem -LiteralPath $target -Recurse -File).Count
if ($fileCount -eq 0) { throw "Storage restore produced no files." }
Write-Host "Storage restore completed. fileCount=$fileCount target=$target"
