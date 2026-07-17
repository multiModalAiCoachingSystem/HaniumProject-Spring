import unittest
from dataclasses import asdict, dataclass

from app.model.nvidia_nemotron_runner import NvidiaNemotronError, NvidiaNemotronRunner
from app.services.video_llm_analyzer import VideoLlmAnalyzer


@dataclass(frozen=True)
class FakeMetadata:
    durationSeconds: float | None = 60.0
    width: int | None = 1280
    height: int | None = 720
    fps: float | None = 30.0
    frameCount: int | None = 1800
    videoCodec: str | None = "h264"
    audioCodec: str | None = "aac"
    formatName: str | None = "mov,mp4"
    sizeBytes: int | None = 1024


class FakeSampler:
    def prepare_samples(self, job_id, file_path, max_frames=8):
        return {
            "metadata": asdict(FakeMetadata()),
            "sampledFrames": [
                {"index": index, "timestampSeconds": float(index * 5), "path": f"frame_{index}.jpg"}
                for index in range(max_frames)
            ],
            "audio": None,
        }


class FailingNvidiaRunner:
    def analyze_frames(self, frames, metadata, job_id=None):
        raise NvidiaNemotronError("simulated failure")


class HttpFailingNvidiaRunner:
    def analyze_frames(self, frames, metadata, job_id=None):
        raise NvidiaNemotronError("simulated HTTP failure", reason_code="NVIDIA_BAD_REQUEST", status_code=400)


class FakeOllamaRunner:
    def __init__(self):
        self.called = False
        self.last_fallback_reason = None

    def analyze_frames(self, frames, metadata):
        self.called = True
        return [
            {
                "type": "INFO",
                "category": "VIDEO_QUALITY",
                "startSeconds": 0.0,
                "endSeconds": 10.0,
                "title": "Ollama fallback",
                "description": "fallback observation",
                "suggestion": "",
            }
        ]


class FailingOllamaRunner:
    def __init__(self):
        self.called = False
        self.last_fallback_reason = None

    def analyze_frames(self, frames, metadata):
        self.called = True
        self.last_fallback_reason = "OLLAMA_REQUEST_FAILED"
        return []


class VideoLlmProviderTest(unittest.TestCase):
    def test_nvidia_failure_uses_ollama_fallback(self):
        ollama = FakeOllamaRunner()
        analyzer = VideoLlmAnalyzer(
            sampler=FakeSampler(),
            runner=ollama,
            nvidia_runner=FailingNvidiaRunner(),
        )
        analyzer.provider = "NVIDIA_NIM"
        analyzer.fallback_provider = "OLLAMA"

        result = analyzer.analyze(job_id=1, video_id=10, file_path="uploads/video.mp4")

        self.assertTrue(ollama.called)
        self.assertEqual(result["provider"], "OLLAMA")
        self.assertTrue(result["metadata"]["fallbackUsed"])
        self.assertEqual(result["metadata"]["fallbackFromProvider"], "NVIDIA_NIM")
        self.assertEqual(result["metadata"]["requestedProvider"], "NVIDIA_NIM")
        self.assertEqual(result["metadata"]["actualProvider"], "OLLAMA")
        self.assertEqual(result["metadata"]["actualModel"], result["model"])
        self.assertIn("requestStartedAt", result["metadata"])
        self.assertIn("requestCompletedAt", result["metadata"])
        self.assertEqual(result["observations"][0]["title"], "Ollama fallback")

    def test_nvidia_http_failure_records_status_without_secret_details(self):
        analyzer = VideoLlmAnalyzer(
            sampler=FakeSampler(),
            runner=FakeOllamaRunner(),
            nvidia_runner=HttpFailingNvidiaRunner(),
        )
        analyzer.provider = "NVIDIA_NIM"
        analyzer.fallback_provider = "OLLAMA"

        result = analyzer.analyze(job_id=1, video_id=10, file_path="uploads/video.mp4")

        self.assertEqual(result["provider"], "OLLAMA")
        self.assertEqual(result["metadata"]["fallbackReason"], "NVIDIA_BAD_REQUEST")
        self.assertEqual(result["metadata"]["fallbackStatusCode"], 400)

    def test_none_provider_returns_deterministic_fallback(self):
        analyzer = VideoLlmAnalyzer(
            sampler=FakeSampler(),
            runner=FakeOllamaRunner(),
            nvidia_runner=FailingNvidiaRunner(),
        )
        analyzer.provider = "NONE"

        result = analyzer.analyze(job_id=1, video_id=10, file_path="uploads/video.mp4")

        self.assertEqual(result["provider"], "NONE")
        self.assertEqual(result["model"], "DETERMINISTIC_FALLBACK")
        self.assertTrue(result["metadata"]["fallbackUsed"])
        self.assertEqual(result["metadata"]["actualProvider"], "NONE")
        self.assertEqual(result["metadata"]["actualModel"], "DETERMINISTIC_FALLBACK")
        self.assertEqual(result["observations"][0]["category"], "VIDEO_QUALITY")

    def test_nvidia_and_ollama_failure_returns_deterministic_fallback_metadata(self):
        ollama = FailingOllamaRunner()
        analyzer = VideoLlmAnalyzer(
            sampler=FakeSampler(),
            runner=ollama,
            nvidia_runner=FailingNvidiaRunner(),
        )
        analyzer.provider = "NVIDIA_NIM"
        analyzer.fallback_provider = "OLLAMA"

        result = analyzer.analyze(job_id=1, video_id=10, file_path="uploads/video.mp4")

        self.assertTrue(ollama.called)
        self.assertEqual(result["provider"], "NONE")
        self.assertEqual(result["model"], "DETERMINISTIC_FALLBACK")
        self.assertTrue(result["metadata"]["fallbackUsed"])
        self.assertEqual(result["metadata"]["fallbackFromProvider"], "NVIDIA_NIM")
        self.assertEqual(result["metadata"]["fallbackAttemptedProvider"], "OLLAMA")
        self.assertEqual(result["metadata"]["fallbackReason"], "OLLAMA_REQUEST_FAILED")

    def test_nemotron_observations_are_normalized_for_backend_schema(self):
        runner = NvidiaNemotronRunner()

        observations = runner._normalize_observations(
            [
                {
                    "timestampSeconds": "12.5",
                    "category": "GAZE",
                    "level": "IMPROVEMENT",
                    "description": "The presenter looks away from the audience.",
                    "confidence": "0.82",
                }
            ],
            [{"timestampSeconds": 12.5}],
        )

        self.assertEqual(observations[0]["type"], "WEAKNESS")
        self.assertEqual(observations[0]["level"], "IMPROVEMENT")
        self.assertEqual(observations[0]["rawLevel"], "IMPROVEMENT")
        self.assertEqual(observations[0]["category"], "EYE_CONTACT")
        self.assertEqual(observations[0]["startSeconds"], 12.5)
        self.assertEqual(observations[0]["confidence"], 0.82)

    def test_nemotron_image_limit_is_capped_for_nvidia_api(self):
        runner = NvidiaNemotronRunner()

        self.assertLessEqual(runner.max_images, 10)
        self.assertGreaterEqual(runner.max_images, 1)


if __name__ == "__main__":
    unittest.main()
