Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-ProjectRoot {
    return [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
}

function Get-EnvValue {
    param(
        [Parameter(Mandatory = $true)][string]$EnvFile,
        [Parameter(Mandatory = $true)][string]$Name
    )

    $processValue = [Environment]::GetEnvironmentVariable($Name)
    if (-not [string]::IsNullOrWhiteSpace($processValue)) {
        return $processValue.Trim()
    }
    if (-not (Test-Path -LiteralPath $EnvFile -PathType Leaf)) {
        return $null
    }

    foreach ($line in Get-Content -LiteralPath $EnvFile -Encoding UTF8) {
        if ($line -match "^\s*$([regex]::Escape($Name))\s*=\s*(.*)\s*$") {
            return $Matches[1].Trim().Trim('"').Trim("'")
        }
    }
    return $null
}

function Resolve-BackupRoot {
    param(
        [string]$BackupRoot,
        [Parameter(Mandatory = $true)][string]$EnvFile
    )

    $candidate = $BackupRoot
    if ([string]::IsNullOrWhiteSpace($candidate)) {
        $candidate = Get-EnvValue -EnvFile $EnvFile -Name "BACKUP_ROOT"
    }
    if ([string]::IsNullOrWhiteSpace($candidate)) {
        throw "BACKUP_ROOT is required. Set it in infra/docker/.env or pass -BackupRoot."
    }

    $resolved = [System.IO.Path]::GetFullPath($candidate)
    New-Item -ItemType Directory -Path $resolved -Force | Out-Null
    return $resolved
}

function Assert-PathWithinRoot {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Root
    )

    $resolvedPath = [System.IO.Path]::GetFullPath($Path)
    $resolvedRoot = [System.IO.Path]::GetFullPath($Root).TrimEnd('\', '/')
    $prefix = $resolvedRoot + [System.IO.Path]::DirectorySeparatorChar
    if ($resolvedPath -ne $resolvedRoot -and
        -not $resolvedPath.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Unsafe path outside backup root: $resolvedPath"
    }
    return $resolvedPath
}

function Get-ComposePaths {
    param([string]$ProjectRoot = (Get-ProjectRoot))
    return @{
        ComposeFile = Join-Path $ProjectRoot "infra\docker\docker-compose.yml"
        EnvFile = Join-Path $ProjectRoot "infra\docker\.env"
    }
}

function Get-MySqlContainerId {
    param(
        [Parameter(Mandatory = $true)][string]$ComposeFile,
        [Parameter(Mandatory = $true)][string]$EnvFile
    )

    $containerId = (& docker.exe compose --env-file $EnvFile -f $ComposeFile ps -q mysql).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
        throw "The Docker Compose mysql container is not running."
    }
    return $containerId
}

function Get-ContainerDatabaseName {
    param([Parameter(Mandatory = $true)][string]$ContainerId)
    $database = Get-ContainerEnvironmentValue -ContainerId $ContainerId -Name "MYSQL_DATABASE"
    if ($LASTEXITCODE -ne 0 -or $database -notmatch '^[A-Za-z0-9_]+$') {
        throw "Could not determine a safe MYSQL_DATABASE name from the container."
    }
    return $database
}

function Get-ContainerEnvironmentValue {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerId,
        [Parameter(Mandatory = $true)][string]$Name
    )

    $json = (& docker.exe inspect --format '{{json .Config.Env}}' $ContainerId)
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($json)) {
        throw "Could not inspect the target container."
    }
    $environmentEntries = ConvertFrom-Json -InputObject $json
    $entry = @($environmentEntries | Where-Object { $_ -like "$Name=*" } | Select-Object -First 1)
    if ($entry.Count -ne 1) {
        throw "Required container environment variable is missing: $Name"
    }
    return ($entry[0] -split "=", 2)[1]
}

function Invoke-MySqlScalar {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerId,
        [Parameter(Mandatory = $true)][string]$Sql,
        [string]$Database
    )

    if ([string]::IsNullOrWhiteSpace($Database)) {
        $Database = Get-ContainerDatabaseName -ContainerId $ContainerId
    }
    if ($Database -notmatch '^[A-Za-z0-9_]+$') { throw "Unsafe database name." }

    $sqlBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Sql))
    $queryPath = "/tmp/mulmoco-query-$([Guid]::NewGuid().ToString('N')).sql"
    $result = (& docker.exe exec -e "MULMOCO_SQL_B64=$sqlBase64" `
        -e "TARGET_DATABASE=$Database" -e "QUERY_PATH=$queryPath" `
        $ContainerId sh -lc @'
set -eu
export MYSQL_PWD="$MYSQL_PASSWORD"
trap 'rm -f -- "$QUERY_PATH"' EXIT
printf '%s' "$MULMOCO_SQL_B64" | base64 -d > "$QUERY_PATH"
mysql --user=$MYSQL_USER --database=$TARGET_DATABASE --skip-column-names < "$QUERY_PATH"
'@)
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL metadata query failed."
    }
    return ($result -join "`n").Trim()
}

function Write-Sha256File {
    param([Parameter(Mandatory = $true)][string]$FilePath)
    $file = Get-Item -LiteralPath $FilePath
    $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    $checksumPath = $file.FullName + ".sha256"
    Set-Content -LiteralPath $checksumPath -Encoding ASCII -Value "$hash *$($file.Name)"
    return $checksumPath
}

function Test-Sha256File {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string]$ChecksumPath
    )
    if (-not (Test-Path -LiteralPath $ChecksumPath -PathType Leaf)) {
        throw "Checksum file was not found: $ChecksumPath"
    }
    $fileName = [System.IO.Path]::GetFileName($FilePath)
    $checksumEntries = @(
        Get-Content -LiteralPath $ChecksumPath -Encoding ASCII |
            ForEach-Object {
                if ($_ -match '^([0-9A-Fa-f]{64})\s+\*?(.+)$') {
                    [pscustomobject]@{
                        Hash = $Matches[1]
                        Name = $Matches[2]
                    }
                }
            }
    )
    $matchingEntry = @($checksumEntries | Where-Object {
        $_.Name.Equals($fileName, [System.StringComparison]::OrdinalIgnoreCase)
    } | Select-Object -First 1)
    if ($matchingEntry.Count -eq 1) {
        $expected = $matchingEntry[0].Hash
    } elseif ($checksumEntries.Count -eq 1) {
        $expected = $checksumEntries[0].Hash
    } else {
        throw "Checksum entry was not found for file: $fileName"
    }
    $actual = (Get-FileHash -LiteralPath $FilePath -Algorithm SHA256).Hash
    if ([string]::IsNullOrWhiteSpace($expected) -or
        -not $actual.Equals($expected, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "SHA-256 checksum mismatch."
    }
}

function Remove-ExpiredBackupItems {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][System.IO.FileSystemInfo[]]$Items,
        [int]$RetentionDays = 14,
        [int]$KeepMinimum = 3
    )

    if ($RetentionDays -lt 1 -or $KeepMinimum -lt 1) {
        throw "Retention days and minimum backup count must be positive."
    }
    $resolvedRoot = [System.IO.Path]::GetFullPath($Root)
    $ordered = @($Items | Sort-Object LastWriteTimeUtc -Descending)
    $cutoff = [DateTime]::UtcNow.AddDays(-$RetentionDays)
    for ($index = $KeepMinimum; $index -lt $ordered.Count; $index++) {
        $item = $ordered[$index]
        if ($item.LastWriteTimeUtc -ge $cutoff -or $item.LastWriteTime.Date -eq (Get-Date).Date) {
            continue
        }
        $safePath = Assert-PathWithinRoot -Path $item.FullName -Root $resolvedRoot
        if ($item.PSIsContainer) {
            Remove-Item -LiteralPath $safePath -Recurse -Force
        } else {
            Remove-Item -LiteralPath $safePath -Force
        }
        Write-Host "Removed expired backup: $($item.Name)"
    }
}
