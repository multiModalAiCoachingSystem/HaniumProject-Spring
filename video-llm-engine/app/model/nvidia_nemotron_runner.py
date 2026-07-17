from __future__ import annotations

import base64
import json
import logging
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

from app.core.config import settings

logger = logging.getLogger(__name__)


class NvidiaNemotronError(RuntimeError):
    """Raised when NVIDIA NIM visual analysis cannot produce a usable response."""

    def __init__(self, message: str, reason_code: str = "NVIDIA_REQUEST_FAILED", status_code: int | None = None) -> None:
        super().__init__(message)
        self.reason_code = reason_code
        self.status_code = status_code


class NvidiaNemotronRunner:
    def __init__(self) -> None:
        self.endpoint = settings.nvidia_api_base_url
        self.model = settings.video_llm_model
        self.api_key = settings.nvidia_api_key
        self.timeout_seconds = max(1, int(settings.nvidia_request_timeout_seconds))
        self.max_images = min(max(1, int(settings.nvidia_max_images)), 10)
        self.max_payload_bytes = int(max(1.0, float(settings.nvidia_max_payload_mb)) * 1024 * 1024)

    def analyze_frames(
        self,
        frames: list[dict[str, Any]],
        metadata: dict[str, Any],
        job_id: int | None = None,
    ) -> dict[str, Any]:
        if not self.api_key:
            raise NvidiaNemotronError("NVIDIA_API_KEY is not configured")

        selected_frames = [frame for frame in frames[: self.max_images] if frame.get("path")]
        if not selected_frames:
            raise NvidiaNemotronError("No sampled frames are available for NVIDIA NIM")

        payload = self._payload(selected_frames, metadata)
        encoded_payload = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        if len(encoded_payload) > self.max_payload_bytes:
            raise NvidiaNemotronError("NVIDIA request payload exceeds configured size limit")

        logger.info(
            "NVIDIA Nemotron request started: jobId=%s model=%s frameCount=%s payloadSizeBytes=%s",
            job_id if job_id is not None else "",
            self.model,
            len(selected_frames),
            len(encoded_payload),
        )

        request = urllib.request.Request(
            self.endpoint,
            data=encoded_payload,
            headers={
                "Authorization": f"Bearer {self.api_key}",
                "Content-Type": "application/json",
                "Accept": "application/json",
            },
            method="POST",
        )

        try:
            with urllib.request.urlopen(request, timeout=self.timeout_seconds) as response:
                status_code = response.status
                data = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            raise NvidiaNemotronError(
                f"NVIDIA NIM HTTP error: {exc.code}",
                reason_code=self._http_reason_code(exc.code),
                status_code=exc.code,
            ) from exc
        except TimeoutError as exc:
            raise NvidiaNemotronError("NVIDIA NIM request timed out", reason_code="NVIDIA_TIMEOUT") from exc
        except urllib.error.URLError as exc:
            reason = str(getattr(exc, "reason", exc))
            reason_code = "NVIDIA_TIMEOUT" if "timed out" in reason.lower() else "NVIDIA_CONNECTION_ERROR"
            raise NvidiaNemotronError("NVIDIA NIM request failed", reason_code=reason_code) from exc
        except json.JSONDecodeError as exc:
            raise NvidiaNemotronError(
                "NVIDIA NIM returned invalid response JSON",
                reason_code="NVIDIA_RESPONSE_INVALID_JSON",
            ) from exc

        content = self._extract_content(data)
        parsed = self._parse_json_object(content)
        observations = self._normalize_observations(parsed.get("observations"), selected_frames)

        logger.info(
            "NVIDIA Nemotron request completed: jobId=%s model=%s statusCode=%s observationCount=%s",
            job_id if job_id is not None else "",
            self.model,
            status_code,
            len(observations),
        )

        return {
            "provider": "NVIDIA_NIM",
            "model": self.model,
            "metadata": {
                "videoLlmResult": self._safe_result_metadata(parsed),
                "nvidia": {
                    "endpoint": self.endpoint,
                    "maxImages": self.max_images,
                    "payloadBytes": len(encoded_payload),
                    "statusCode": status_code,
                    "frameCount": len(selected_frames),
                },
            },
            "observations": observations,
        }

    def _payload(self, frames: list[dict[str, Any]], metadata: dict[str, Any]) -> dict[str, Any]:
        content: list[dict[str, Any]] = [
            {
                "type": "text",
                "text": self._user_prompt(metadata, frames),
            }
        ]
        for frame in frames:
            content.append(
                {
                    "type": "image_url",
                    "image_url": {
                        "url": self._image_data_url(frame["path"]),
                    },
                }
            )

        return {
            "model": self.model,
            "messages": [
                {
                    "role": "system",
                    "content": "/think\n" + self._system_prompt(),
                },
                {
                    "role": "user",
                    "content": content,
                },
            ],
            "stream": False,
            "max_tokens": min(max(1, int(settings.nvidia_max_tokens)), 4096),
            "temperature": float(settings.nvidia_temperature),
            "top_p": float(settings.nvidia_top_p),
        }

    def _image_data_url(self, path: str) -> str:
        file_path = Path(path)
        mime_type = self._mime_type(file_path)
        encoded = base64.b64encode(file_path.read_bytes()).decode("utf-8")
        return f"data:{mime_type};base64,{encoded}"

    @staticmethod
    def _mime_type(path: Path) -> str:
        suffix = path.suffix.lower().lstrip(".")
        return {
            "png": "image/png",
            "jpg": "image/jpeg",
            "jpeg": "image/jpeg",
            "webp": "image/webp",
        }.get(suffix, "image/jpeg")

    @staticmethod
    def _system_prompt() -> str:
        return (
            "You are a visual presentation coaching model. Return valid JSON only. "
            "Do not use Markdown code fences. Describe only directly observable visual behavior. "
            "Do not infer identity or sensitive personal attributes. Do not invent speech metrics, "
            "STT accuracy, filler words, silence ratio, or final scores. Use cautious wording for "
            "tension or confidence estimates."
        )

    @staticmethod
    def _user_prompt(metadata: dict[str, Any], frames: list[dict[str, Any]]) -> str:
        frame_info = [
            {
                "index": frame.get("index"),
                "timestampSeconds": frame.get("timestampSeconds"),
            }
            for frame in frames
        ]
        return f"""
Analyze the sampled presentation video frames.

Video metadata:
{json.dumps(metadata, ensure_ascii=False)}

Sampled frame timestamps:
{json.dumps(frame_info, ensure_ascii=False)}

Return JSON using this schema:
{{
  "provider": "NVIDIA_NIM",
  "model": "nvidia/nemotron-nano-12b-v2-vl",
  "summary": "Factual visual summary in English",
  "observations": [
    {{
      "timestampSeconds": 12.5,
      "category": "POSTURE or GAZE or GESTURE or EXPRESSION or VISIBILITY or GENERAL",
      "level": "STRENGTH or IMPROVEMENT or NEUTRAL",
      "description": "Directly observable visual behavior",
      "confidence": 0.82
    }}
  ],
  "visualMetrics": {{
    "postureAssessment": "",
    "gazeAssessment": "",
    "gestureAssessment": "",
    "expressionAssessment": ""
  }},
  "fallbackUsed": false
}}
""".strip()

    def _extract_content(self, data: dict[str, Any]) -> str:
        choices = data.get("choices")
        if not isinstance(choices, list) or not choices:
            raise NvidiaNemotronError("NVIDIA NIM response is missing choices")
        message = choices[0].get("message") if isinstance(choices[0], dict) else None
        if not isinstance(message, dict) or message.get("content") is None:
            raise NvidiaNemotronError("NVIDIA NIM response is missing message content")
        content = message["content"]
        if isinstance(content, list):
            text_parts = [
                item.get("text", "")
                for item in content
                if isinstance(item, dict) and item.get("type") in (None, "text")
            ]
            content = "\n".join(text_parts)
        content = str(content).strip()
        if not content:
            raise NvidiaNemotronError("NVIDIA NIM returned blank message content")
        return content

    def _parse_json_object(self, raw: str) -> dict[str, Any]:
        source = raw.strip()
        if source.startswith("```"):
            source = source.replace("```json", "```", 1)
            source = source.strip("`").strip()
        start = source.find("{")
        end = source.rfind("}")
        if start < 0 or end <= start:
            raise NvidiaNemotronError("NVIDIA NIM response does not contain a JSON object")
        try:
            parsed = json.loads(source[start : end + 1])
        except json.JSONDecodeError as exc:
            raise NvidiaNemotronError("NVIDIA NIM message content is invalid JSON") from exc
        if not isinstance(parsed, dict):
            raise NvidiaNemotronError("NVIDIA NIM JSON content is not an object")
        return parsed

    def _normalize_observations(self, observations: Any, frames: list[dict[str, Any]]) -> list[dict[str, Any]]:
        if not isinstance(observations, list):
            return []

        frame_times = [
            float(frame.get("timestampSeconds") or 0.0)
            for frame in frames
            if isinstance(frame, dict)
        ]
        normalized = []
        for index, item in enumerate(observations, start=1):
            if not isinstance(item, dict):
                continue
            fallback_start = frame_times[min(index - 1, len(frame_times) - 1)] if frame_times else 0.0
            timestamp = self._float_or_default(item.get("timestampSeconds"), fallback_start)
            level = str(item.get("level") or "NEUTRAL").upper()
            category = self._category(item.get("category"))
            normalized_level = self._normalized_level(level)
            normalized.append(
                {
                    "id": index,
                    "type": self._type(normalized_level),
                    "level": normalized_level,
                    "rawLevel": level,
                    "category": category,
                    "startSeconds": round(timestamp, 3),
                    "endSeconds": round(timestamp + 10.0, 3),
                    "title": self._title(normalized_level, category),
                    "description": str(item.get("description") or ""),
                    "suggestion": self._suggestion(normalized_level),
                    "confidence": self._float_or_default(item.get("confidence"), 0.0),
                }
            )
        return normalized

    @staticmethod
    def _category(value: Any) -> str:
        category = str(value or "GENERAL").upper()
        return {
            "GAZE": "EYE_CONTACT",
            "GENERAL": "VIDEO_QUALITY",
            "VISIBILITY": "VIDEO_QUALITY",
        }.get(category, category)

    @staticmethod
    def _normalized_level(value: str) -> str:
        return {
            "STRENGTH": "STRENGTH",
            "IMPROVEMENT": "IMPROVEMENT",
            "NEEDS_IMPROVEMENT": "IMPROVEMENT",
            "WEAKNESS": "IMPROVEMENT",
            "NEUTRAL": "NEUTRAL",
        }.get(value, "NEUTRAL")

    @staticmethod
    def _type(level: str) -> str:
        return {
            "STRENGTH": "STRENGTH",
            "IMPROVEMENT": "WEAKNESS",
            "NEUTRAL": "INFO",
        }.get(level, "INFO")

    @staticmethod
    def _title(level: str, category: str) -> str:
        if level == "STRENGTH":
            return f"{category} visual strength"
        if level == "IMPROVEMENT":
            return f"{category} improvement point"
        return f"{category} visual observation"

    @staticmethod
    def _suggestion(level: str) -> str:
        if level == "IMPROVEMENT":
            return "Review this visual behavior and adjust it in the next rehearsal."
        if level == "STRENGTH":
            return "Keep this visual behavior consistent in future presentations."
        return ""

    @staticmethod
    def _float_or_default(value: Any, default: float) -> float:
        try:
            return float(value)
        except (TypeError, ValueError):
            return float(default)

    @staticmethod
    def _safe_result_metadata(parsed: dict[str, Any]) -> dict[str, Any]:
        return {
            "provider": parsed.get("provider"),
            "model": parsed.get("model"),
            "summary": parsed.get("summary"),
            "visualMetrics": parsed.get("visualMetrics"),
            "fallbackUsed": parsed.get("fallbackUsed", False),
        }

    @staticmethod
    def _http_reason_code(status_code: int) -> str:
        return {
            400: "NVIDIA_BAD_REQUEST",
            401: "NVIDIA_AUTHENTICATION_ERROR",
            403: "NVIDIA_FORBIDDEN",
            413: "NVIDIA_PAYLOAD_TOO_LARGE",
            422: "NVIDIA_VALIDATION_ERROR",
            429: "NVIDIA_RATE_LIMITED",
        }.get(status_code, "NVIDIA_SERVER_ERROR" if status_code >= 500 else "NVIDIA_HTTP_ERROR")
