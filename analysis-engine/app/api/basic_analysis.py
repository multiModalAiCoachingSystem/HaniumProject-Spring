# 역할: analysis-engine의 HTTP 엔드포인트를 정의하고 분석 요청을 서비스 계층으로 전달합니다.
from __future__ import annotations

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.services.basic_analyzer import BasicAnalyzer
from app.services.video_info import MediaInfoError

router = APIRouter(prefix="/api", tags=["basic-analysis"])


class BasicAnalysisRequest(BaseModel):
    jobId: int = Field(..., ge=1)
    videoId: int = Field(..., ge=1)
    filePath: str = Field(..., min_length=1)
    transcript: str | None = None


# 함수 역할: analyze에서 발표 분석 요청, 엔진 호출, 결과 처리 흐름을 조율합니다.
@router.post("/basic-analysis")
def analyze(request: BasicAnalysisRequest) -> dict:
    try:
        return BasicAnalyzer().analyze(
            job_id=request.jobId,
            video_id=request.videoId,
            file_path=request.filePath,
            transcript=request.transcript,
        )
    except MediaInfoError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
