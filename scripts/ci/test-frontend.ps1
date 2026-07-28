param()

. "$PSScriptRoot\Ci.Common.ps1"

$root = Resolve-Path "$PSScriptRoot\..\.."
$frontend = Join-Path $root "frontend"

Invoke-CiCommand "frontend-install" $frontend "npm" @("ci")
Invoke-CiCommand "frontend-lint" $frontend "npm" @("run", "lint")
Invoke-CiCommand "frontend-test" $frontend "npm" @("test")
Invoke-CiCommand "frontend-build" $frontend "npm" @("run", "build")
