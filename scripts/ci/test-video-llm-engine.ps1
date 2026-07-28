param()

. "$PSScriptRoot\Ci.Common.ps1"

$root = Resolve-Path "$PSScriptRoot\..\.."
$engine = Join-Path $root "video-llm-engine"
$previousProvider = $env:VIDEO_LLM_PROVIDER
$previousFallback = $env:VIDEO_LLM_FALLBACK_PROVIDER
$previousNvidiaKey = $env:NVIDIA_API_KEY

try {
    $env:VIDEO_LLM_PROVIDER = "NONE"
    $env:VIDEO_LLM_FALLBACK_PROVIDER = "NONE"
    $env:NVIDIA_API_KEY = ""

    Invoke-CiCommand "video-llm-ffmpeg" $engine "ffmpeg" @("-version")
    Invoke-CiCommand "video-llm-imports" $engine "python" @("-c", "from app.main import app; assert app")
    Invoke-CiCommand "video-llm-tests" $engine "python" @("-m", "unittest", "discover", "-s", "tests", "-v")
    Invoke-CiCommand "video-llm-compile" $engine "python" @("-m", "compileall", "-q", "app")
}
finally {
    $env:VIDEO_LLM_PROVIDER = $previousProvider
    $env:VIDEO_LLM_FALLBACK_PROVIDER = $previousFallback
    $env:NVIDIA_API_KEY = $previousNvidiaKey
}
