param()

. "$PSScriptRoot\Ci.Common.ps1"

$root = Resolve-Path "$PSScriptRoot\..\.."
$engine = Join-Path $root "analysis-engine"

Invoke-CiCommand "analysis-ffmpeg" $engine "ffmpeg" @("-version")
Invoke-CiCommand "analysis-imports" $engine "python" @("-c", "import cv2, mediapipe; from app.main import app; assert app")
Invoke-CiCommand "analysis-tests" $engine "python" @("-m", "unittest", "discover", "-s", "tests", "-v")
Invoke-CiCommand "analysis-compile" $engine "python" @("-m", "compileall", "-q", "app")
