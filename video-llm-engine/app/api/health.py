# 역할: video-llm-engine의 HTTP 엔드포인트를 정의하고 영상 LLM 분석 요청을 처리합니다.
from __future__ import annotations

from fastapi import APIRouter

from app.services.ffmpeg_service import FFmpegError, FFmpegService

router = APIRouter(tags=["health"])


# 함수 역할: health에서 서비스 상태 확인 요청에 대한 응답을 반환합니다.
@router.get("/health")
def health() -> dict:
    try:
        FFmpegService().ensure_available()
        ffmpeg = "UP"
    except FFmpegError as exc:
        ffmpeg = f"DOWN: {exc}"

    return {"status": "UP", "ffmpeg": ffmpeg}
