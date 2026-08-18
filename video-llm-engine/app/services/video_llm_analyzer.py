from __future__ import annotations

import logging
import time
from datetime import datetime, timezone

from app.core.config import settings
from app.model.nvidia_nemotron_runner import NvidiaNemotronError, NvidiaNemotronRunner
from app.model.qwen_vl_runner import QwenVlRunner
from app.services.video_sampler import VideoSampler

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)


class VideoLlmAnalyzer:
    def __init__(
        self,
        sampler: VideoSampler | None = None,
        runner: QwenVlRunner | None = None,
        nvidia_runner: NvidiaNemotronRunner | None = None,
    ) -> None:
        self.sampler = sampler or VideoSampler()
        self.ollama_runner = runner or QwenVlRunner()
        self.nvidia_runner = nvidia_runner or NvidiaNemotronRunner()
        self.provider = settings.video_llm_provider.upper()
        self.fallback_provider = settings.video_llm_fallback_provider.upper()

    def analyze(self, job_id: int, video_id: int, file_path: str) -> dict:
        started = time.perf_counter()
        logger.info("[VIDEO_LLM] 비디오 LLM 분석을 시작합니다. jobId=%s", job_id)
        samples = self.sampler.prepare_samples(job_id=job_id, file_path=file_path)
        metadata = samples["metadata"]
        frames = samples["sampledFrames"]

        requested_provider = self.provider
        requested_model = self._requested_model(requested_provider)
        request_started_at = self._utc_now()
        logger.info(
            "[VIDEO_LLM] Provider를 선택했습니다. jobId=%s requestedProvider=%s requestedModel=%s "
            "inputType=IMAGE_FRAMES frameCount=%s",
            job_id,
            requested_provider,
            requested_model,
            len(frames),
        )
        result = self._analyze_with_provider(self.provider, metadata, frames, job_id)
        request_completed_at = self._utc_now()
        response_metadata = {
            **metadata,
            **result.get("metadata", {}),
        }
        response_metadata.update(
            {
                "jobId": job_id,
                "requestedProvider": requested_provider,
                "actualProvider": result["provider"],
                "requestedModel": requested_model,
                "actualModel": result["model"],
                "fallbackUsed": bool(response_metadata.get("fallbackUsed", False)),
                "requestStartedAt": request_started_at,
                "requestCompletedAt": request_completed_at,
            }
        )
        if response_metadata.get("fallbackReason") is None:
            response_metadata.pop("fallbackReason", None)

        logger.info(
            "[VIDEO_LLM] 비디오 LLM 분석이 끝났습니다. jobId=%s requestedProvider=%s actualProvider=%s "
            "requestedModel=%s actualModel=%s fallbackUsed=%s fallbackReason=%s "
            "requestStartedAt=%s requestCompletedAt=%s elapsed=%.2fs",
            job_id,
            requested_provider,
            result["provider"],
            requested_model,
            result["model"],
            response_metadata["fallbackUsed"],
            response_metadata.get("fallbackReason", ""),
            request_started_at,
            request_completed_at,
            time.perf_counter() - started,
        )

        return {
            "jobId": job_id,
            "videoId": video_id,
            "provider": result["provider"],
            "model": result["model"],
            "metadata": response_metadata,
            "sampledFrames": frames,
            "audio": samples["audio"],
            "observations": result["observations"],
        }

    def _analyze_with_provider(self, provider: str, metadata: dict, frames: list[dict], job_id: int) -> dict:
        if provider == "NVIDIA_NIM":
            try:
                return self.nvidia_runner.analyze_frames(frames, metadata, job_id=job_id)
            except NvidiaNemotronError as exc:
                fallback_reason = self._nvidia_fallback_reason(exc)
                fallback_status_code = getattr(exc, "status_code", None)
                logger.warning(
                    "[VIDEO_LLM] NVIDIA 호출에 실패하여 fallback을 사용합니다. "
                    "jobId=%s provider=NVIDIA_NIM fallbackProvider=%s reason=%s statusCode=%s",
                    job_id,
                    self.fallback_provider,
                    fallback_reason,
                    fallback_status_code or "",
                )
                if self.fallback_provider == "OLLAMA":
                    return self._ollama_result(
                        metadata,
                        frames,
                        fallback_from="NVIDIA_NIM",
                        fallback_reason=fallback_reason,
                        fallback_status_code=fallback_status_code,
                    )
                return self._fallback_result(
                    metadata,
                    frames,
                    fallback_from="NVIDIA_NIM",
                    fallback_reason=fallback_reason,
                    fallback_status_code=fallback_status_code,
                )

        if provider == "OLLAMA":
            return self._ollama_result(metadata, frames)

        if provider == "NONE":
            return self._fallback_result(metadata, frames, fallback_from="NONE", fallback_reason="PROVIDER_NONE")

        logger.warning("[VIDEO_LLM] 지원하지 않는 Provider라 fallback을 사용합니다. jobId=%s provider=%s", job_id, provider)
        return self._fallback_result(metadata, frames, fallback_from=provider, fallback_reason="UNSUPPORTED_PROVIDER")

    def _ollama_result(
        self,
        metadata: dict,
        frames: list[dict],
        fallback_from: str | None = None,
        fallback_reason: str | None = None,
        fallback_status_code: int | None = None,
    ) -> dict:
        observations = self.ollama_runner.analyze_frames(frames, metadata)
        ollama_fallback_reason = getattr(self.ollama_runner, "last_fallback_reason", None)
        if ollama_fallback_reason:
            return self._fallback_result(
                metadata,
                frames,
                fallback_from=fallback_from or "OLLAMA",
                fallback_reason=ollama_fallback_reason,
                fallback_attempted_provider="OLLAMA",
            )
        result_metadata = {
            "fallbackUsed": fallback_from is not None,
        }
        if fallback_from:
            result_metadata["fallbackFromProvider"] = fallback_from
        if fallback_reason:
            result_metadata["fallbackReason"] = fallback_reason
        if fallback_status_code is not None:
            result_metadata["fallbackStatusCode"] = fallback_status_code
        return {
            "provider": "OLLAMA",
            "model": settings.ollama_video_llm_model,
            "metadata": result_metadata,
            "observations": observations,
        }

    def _fallback_result(
        self,
        metadata: dict,
        frames: list[dict],
        fallback_from: str | None = None,
        fallback_reason: str | None = None,
        fallback_status_code: int | None = None,
        fallback_attempted_provider: str | None = None,
    ) -> dict:
        result_metadata = {
            "fallbackUsed": True,
        }
        if fallback_from:
            result_metadata["fallbackFromProvider"] = fallback_from
        if fallback_reason:
            result_metadata["fallbackReason"] = fallback_reason
        if fallback_status_code is not None:
            result_metadata["fallbackStatusCode"] = fallback_status_code
        if fallback_attempted_provider:
            result_metadata["fallbackAttemptedProvider"] = fallback_attempted_provider
        return {
            "provider": "NONE",
            "model": "DETERMINISTIC_FALLBACK",
            "metadata": result_metadata,
            "observations": self._fallback_observations(metadata, frames),
        }

    @staticmethod
    def _requested_model(provider: str) -> str:
        if provider == "NVIDIA_NIM":
            return settings.video_llm_model
        if provider == "OLLAMA":
            return settings.ollama_video_llm_model
        return "DETERMINISTIC_FALLBACK"

    @staticmethod
    def _utc_now() -> str:
        return datetime.now(timezone.utc).isoformat()

    @staticmethod
    def _nvidia_fallback_reason(exc: NvidiaNemotronError) -> str:
        reason_code = getattr(exc, "reason_code", None)
        if reason_code:
            return str(reason_code)
        message = str(exc)
        if "API_KEY" in message:
            return "NVIDIA_API_KEY_MISSING"
        if "HTTP error" in message:
            return "NVIDIA_HTTP_ERROR"
        if "payload" in message:
            return "NVIDIA_PAYLOAD_TOO_LARGE"
        if "invalid response JSON" in message or "invalid JSON" in message:
            return "NVIDIA_RESPONSE_INVALID_JSON"
        if "blank" in message:
            return "NVIDIA_RESPONSE_EMPTY"
        if "No sampled frames" in message:
            return "NO_SAMPLED_FRAMES"
        return "NVIDIA_REQUEST_FAILED"

    def _fallback_observations(self, metadata: dict, frames: list[dict]) -> list[dict]:
        duration = metadata.get("durationSeconds") or 0
        end = min(duration, 12.0) if duration else 12.0
        frame_count = len(frames)

        return [
            {
                "type": "INFO",
                "category": "VIDEO_QUALITY",
                "startSeconds": 0.0,
                "endSeconds": end,
                "title": "Video LLM fallback",
                "description": f"Prepared {frame_count} representative frame(s), but no external Video LLM result was used.",
                "suggestion": "Set VIDEO_LLM_PROVIDER=NVIDIA_NIM or OLLAMA with valid model configuration to enable visual LLM review.",
            }
        ]
