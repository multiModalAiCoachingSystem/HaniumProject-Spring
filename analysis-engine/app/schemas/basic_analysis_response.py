# 역할: analysis-engine API 요청/응답 데이터 구조를 정의하는 Pydantic 스키마입니다.
from __future__ import annotations

from typing import Any

from pydantic import BaseModel


class BasicAnalysisResponse(BaseModel):
    jobId: int
    videoId: int
    durationSeconds: float
    videoInfo: dict[str, Any]
    transcript: str
    stt: dict[str, Any]
    metrics: dict[str, Any]
    scores: list[dict[str, Any]]
    totalScore: float
    summary: dict[str, Any]
    observations: list[dict[str, Any]]
