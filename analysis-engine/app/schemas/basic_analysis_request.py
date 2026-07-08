# 역할: analysis-engine API 요청/응답 데이터 구조를 정의하는 Pydantic 스키마입니다.
from __future__ import annotations

from pydantic import BaseModel, Field


class BasicAnalysisRequest(BaseModel):
    jobId: int = Field(..., ge=1)
    videoId: int = Field(..., ge=1)
    filePath: str = Field(..., min_length=1)
    transcript: str | None = None
