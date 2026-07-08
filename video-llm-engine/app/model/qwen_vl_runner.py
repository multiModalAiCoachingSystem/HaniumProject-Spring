# 역할: 샘플 프레임 이미지를 Qwen-VL/Ollama에 보내고 시각 관찰 피드백을 파싱합니다.
from __future__ import annotations

import base64
import json
import urllib.error
import urllib.request
from pathlib import Path

from app.core.config import settings


class QwenVlRunner:
    # 함수 역할: __init__에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def __init__(self) -> None:
        self.base_url = settings.ollama_base_url.rstrip("/")
        self.model = settings.video_llm_model

    # 함수 역할: analyze_frames에서 발표 분석 요청, 엔진 호출, 결과 처리 흐름을 조율합니다.
    def analyze_frames(self, frames: list[dict], metadata: dict) -> list[dict]:
        image_paths = [frame["path"] for frame in frames[:4] if frame.get("path")]
        if not image_paths:
            return []

        payload = {
            "model": self.model,
            "prompt": self._prompt(metadata, frames[:4]),
            "images": [self._encode_image(path) for path in image_paths],
            "stream": False,
            "format": "json",
        }

        request = urllib.request.Request(
            f"{self.base_url}/api/generate",
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )

        try:
            with urllib.request.urlopen(request, timeout=240) as response:
                data = json.loads(response.read().decode("utf-8"))
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
            return [self._fallback_observation(f"Qwen-VL request failed: {exc}")]

        return self._parse_observations(data.get("response", ""), frames[:4])

    # 함수 역할: _prompt에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def _prompt(self, metadata: dict, frames: list[dict]) -> str:
        return f"""
You are MulMoCo's Qwen-VL presentation coaching model.
Analyze the provided video frame samples. Focus only on visible evidence.
Return ONLY valid JSON. Do not wrap it in markdown.

Video metadata:
{json.dumps(metadata, ensure_ascii=False)}

Sampled frame timestamps:
{json.dumps([{"index": frame.get("index"), "timestampSeconds": frame.get("timestampSeconds")} for frame in frames], ensure_ascii=False)}

Use only the sampled frame timestamps above for startSeconds and endSeconds. If an issue is visible in a frame, set startSeconds near that frame timestamp and endSeconds about 8 to 12 seconds later.

JSON schema:
{{
  "observations": [
    {{
      "type": "STRENGTH or WEAKNESS or INFO",
      "category": "POSTURE or EYE_CONTACT or EXPRESSION or GESTURE or VIDEO_QUALITY",
      "startSeconds": 0,
      "endSeconds": 12,
      "title": "short Korean title",
      "description": "specific Korean visual observation",
      "suggestion": "specific Korean coaching suggestion"
    }}
  ]
}}
""".strip()

    # 함수 역할: _encode_image에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def _encode_image(self, path: str) -> str:
        return base64.b64encode(Path(path).read_bytes()).decode("utf-8")

    # 함수 역할: _parse_observations에서 외부 응답이나 원본 데이터를 내부에서 쓰기 좋은 구조로 해석합니다.
    def _parse_observations(self, raw: str, frames: list[dict] | None = None) -> list[dict]:
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            return [self._fallback_observation(raw[:800])]

        observations = parsed.get("observations", [])
        if not isinstance(observations, list):
            return []

        frame_times = [
            float(frame.get("timestampSeconds") or 0.0)
            for frame in (frames or [])
            if isinstance(frame, dict)
        ]
        normalized = []
        for index, item in enumerate(observations, start=1):
            if not isinstance(item, dict):
                continue
            fallback_start = frame_times[min(index - 1, len(frame_times) - 1)] if frame_times else 0.0
            start_seconds = float(item.get("startSeconds", fallback_start) or fallback_start)
            end_seconds = float(item.get("endSeconds", start_seconds + 10.0) or start_seconds + 10.0)
            if end_seconds <= start_seconds:
                end_seconds = start_seconds + 10.0
            normalized.append(
                {
                    "id": item.get("id", index),
                    "type": item.get("type", "INFO"),
                    "category": item.get("category", "VIDEO_QUALITY"),
                    "startSeconds": round(start_seconds, 3),
                    "endSeconds": round(end_seconds, 3),
                    "title": item.get("title", "Qwen-VL frame analysis"),
                    "description": item.get("description", ""),
                    "suggestion": item.get("suggestion", ""),
                }
            )
        return normalized

    # 함수 역할: _fallback_observation에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    @staticmethod
    def _fallback_observation(message: str) -> dict:
        return {
            "type": "INFO",
            "category": "VIDEO_QUALITY",
            "startSeconds": 0.0,
            "endSeconds": 12.0,
            "title": "Qwen-VL 분석 결과",
            "description": message,
            "suggestion": "",
        }


OllamaVlmRunner = QwenVlRunner
