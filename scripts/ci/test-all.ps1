param()

$ErrorActionPreference = "Stop"

& "$PSScriptRoot\test-backend.ps1"
& "$PSScriptRoot\test-frontend.ps1"
& "$PSScriptRoot\test-analysis-engine.ps1"
& "$PSScriptRoot\test-video-llm-engine.ps1"
& "$PSScriptRoot\validate-compose.ps1"
