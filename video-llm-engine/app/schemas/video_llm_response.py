# 역할: video-llm-engine API 요청/응답과 관찰 결과 데이터 구조를 정의합니다.
from __future__ import annotations

from typing import Any

from pydantic import BaseModel


class VideoLlmResponse(BaseModel):
    jobId: int
    videoId: int
    provider: str
    model: str
    metadata: dict[str, Any]
    sampledFrames: list[dict[str, Any]]
    audio: dict[str, Any] | None
    observations: list[dict[str, Any]]
