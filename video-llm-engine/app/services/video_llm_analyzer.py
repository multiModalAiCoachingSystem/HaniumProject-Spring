# 역할: 샘플링 결과와 LLM runner를 조합해 영상 관찰 분석 결과를 생성합니다.
from __future__ import annotations

from app.core.config import settings
from app.model.qwen_vl_runner import QwenVlRunner
from app.services.video_sampler import VideoSampler


class VideoLlmAnalyzer:
    # 함수 역할: __init__에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def __init__(self, sampler: VideoSampler | None = None, runner: QwenVlRunner | None = None) -> None:
        self.sampler = sampler or VideoSampler()
        self.runner = runner or QwenVlRunner()
        self.provider = settings.video_llm_provider.upper()

    # 함수 역할: analyze에서 발표 분석 요청, 엔진 호출, 결과 처리 흐름을 조율합니다.
    def analyze(self, job_id: int, video_id: int, file_path: str) -> dict:
        samples = self.sampler.prepare_samples(job_id=job_id, file_path=file_path)
        metadata = samples["metadata"]
        frames = samples["sampledFrames"]

        if self.provider == "OLLAMA":
            observations = self.runner.analyze_frames(frames, metadata)
        else:
            observations = self._mock_observations(metadata, frames)

        return {
            "jobId": job_id,
            "videoId": video_id,
            "provider": self.provider,
            "model": settings.video_llm_model if self.provider == "OLLAMA" else "MOCK",
            "metadata": metadata,
            "sampledFrames": frames,
            "audio": samples["audio"],
            "observations": observations,
        }

    # 함수 역할: _mock_observations에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def _mock_observations(self, metadata: dict, frames: list[dict]) -> list[dict]:
        duration = metadata.get("durationSeconds") or 0
        end = min(duration, 12.0) if duration else 12.0
        frame_count = len(frames)

        return [
            {
                "type": "INFO",
                "category": "VIDEO_QUALITY",
                "startSeconds": 0.0,
                "endSeconds": end,
                "title": "Mock frame preparation",
                "description": f"Prepared {frame_count} representative frame(s) for Qwen-VL review.",
                "suggestion": "Set VIDEO_LLM_PROVIDER=OLLAMA to use qwen3-vl:4b.",
            }
        ]
