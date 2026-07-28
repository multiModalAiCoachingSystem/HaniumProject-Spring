param(
    [string]$EnvFile
)

. "$PSScriptRoot\Ci.Common.ps1"

$root = Resolve-Path "$PSScriptRoot\..\.."
if (-not $EnvFile) {
    $EnvFile = Join-Path $root "infra\docker\.env.ci.example"
}

$composeFile = Join-Path $root "infra\docker\docker-compose.yml"
Invoke-CiCommand "compose-config" $root "docker" @(
    "compose",
    "--env-file", $EnvFile,
    "-f", $composeFile,
    "config",
    "--quiet"
)
