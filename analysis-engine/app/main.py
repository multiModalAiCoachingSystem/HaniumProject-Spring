# 역할: FastAPI 앱을 생성하고 health/basic-analysis 라우터를 등록하는 진입점입니다.
from __future__ import annotations

import logging

from fastapi import FastAPI

from app.api.basic_analysis import router as basic_analysis_router
from app.api.health import router as health_router
from app.services.vision_metrics import mediapipe_capabilities


logger = logging.getLogger("uvicorn.error")

app = FastAPI(title="MulMoCo Analysis Engine")
app.include_router(health_router)
app.include_router(basic_analysis_router)


@app.on_event("startup")
def log_mediapipe_capabilities() -> None:
    capabilities = mediapipe_capabilities()
    logger.info(
        "MediaPipe capabilities: version=%s solutionsAvailable=%s poseAvailable=%s faceMeshAvailable=%s handsAvailable=%s",
        capabilities["version"],
        capabilities["solutionsAvailable"],
        capabilities["poseAvailable"],
        capabilities["faceMeshAvailable"],
        capabilities["handsAvailable"],
    )
