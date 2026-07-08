# 역할: video-llm-engine의 HTTP 엔드포인트를 정의하고 영상 LLM 분석 요청을 처리합니다.
from __future__ import annotations

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.services.ffmpeg_service import FFmpegError
from app.services.video_llm_analyzer import VideoLlmAnalyzer

router = APIRouter(prefix="/api", tags=["video-llm-analysis"])


class VideoLlmRequest(BaseModel):
    jobId: int = Field(..., ge=1)
    videoId: int = Field(..., ge=1)
    filePath: str = Field(..., min_length=1)


# 함수 역할: analyze에서 발표 분석 요청, 엔진 호출, 결과 처리 흐름을 조율합니다.
@router.post("/video-llm-analysis")
def analyze(request: VideoLlmRequest) -> dict:
    analyzer = VideoLlmAnalyzer()
    try:
        return analyzer.analyze(
            job_id=request.jobId,
            video_id=request.videoId,
            file_path=request.filePath,
        )
    except FFmpegError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
