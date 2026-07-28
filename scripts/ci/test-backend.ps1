param()

. "$PSScriptRoot\Ci.Common.ps1"

$root = Resolve-Path "$PSScriptRoot\..\.."
$backend = Join-Path $root "backend"
$gradle = if ($IsWindows -or $env:OS -eq "Windows_NT") { ".\gradlew.bat" } else { "./gradlew" }

Invoke-CiCommand "backend-test" $backend $gradle @("clean", "test", "--no-daemon")
Invoke-CiCommand "backend-build" $backend $gradle @("clean", "build", "--no-daemon")
Invoke-CiCommand "backend-bootJar" $backend $gradle @("bootJar", "--no-daemon")
Invoke-CiCommand "backend-deprecation-check" $backend $gradle @("build", "--warning-mode", "all", "--no-daemon")
