# 역할: FastAPI 앱을 생성하고 health/video-llm-analysis 라우터를 등록하는 진입점입니다.
from __future__ import annotations

from fastapi import FastAPI

from app.api.health import router as health_router
from app.api.video_llm_analysis import router as video_llm_router

app = FastAPI(title="MulMoCo Video LLM Engine")
app.include_router(health_router)
app.include_router(video_llm_router)
