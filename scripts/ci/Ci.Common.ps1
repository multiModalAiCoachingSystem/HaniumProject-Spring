$ErrorActionPreference = "Stop"

function Invoke-CiCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string]$WorkingDirectory,
        [Parameter(Mandatory = $true)]
        [string]$Executable,
        [string[]]$Arguments = @()
    )

    $startedAt = Get-Date
    Write-Host "[$Name] startedAt=$($startedAt.ToString('o'))"

    Push-Location $WorkingDirectory
    try {
        & $Executable @Arguments
        $exitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }

    $completedAt = Get-Date
    Write-Host "[$Name] completedAt=$($completedAt.ToString('o')) exitCode=$exitCode"

    if ($exitCode -ne 0) {
        throw "$Name failed with exit code $exitCode."
    }
}
