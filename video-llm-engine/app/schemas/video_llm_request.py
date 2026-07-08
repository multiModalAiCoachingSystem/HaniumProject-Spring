# 역할: video-llm-engine API 요청/응답과 관찰 결과 데이터 구조를 정의합니다.
from __future__ import annotations

from pydantic import BaseModel, Field


class VideoLlmRequest(BaseModel):
    jobId: int = Field(..., ge=1)
    videoId: int = Field(..., ge=1)
    filePath: str = Field(..., min_length=1)
