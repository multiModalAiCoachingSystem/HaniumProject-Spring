# 역할: FastAPI 앱을 생성하고 health/video-llm-analysis 라우터를 등록하는 진입점입니다.
from __future__ import annotations

import logging

from fastapi import FastAPI

from app.api.health import router as health_router
from app.api.video_llm_analysis import router as video_llm_router


def configure_app_logging() -> None:
    uvicorn_logger = logging.getLogger("uvicorn")
    app_logger = logging.getLogger("app")
    app_logger.handlers = list(uvicorn_logger.handlers)
    app_logger.setLevel(logging.INFO)
    app_logger.propagate = False


configure_app_logging()

app = FastAPI(title="MulMoCo Video LLM Engine")
app.include_router(health_router)
app.include_router(video_llm_router)
