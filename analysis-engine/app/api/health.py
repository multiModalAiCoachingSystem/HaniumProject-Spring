# 역할: analysis-engine의 HTTP 엔드포인트를 정의하고 분석 요청을 서비스 계층으로 전달합니다.
from __future__ import annotations

from fastapi import APIRouter

router = APIRouter(tags=["health"])


# 함수 역할: health에서 서비스 상태 확인 요청에 대한 응답을 반환합니다.
@router.get("/health")
def health() -> dict:
    return {"status": "UP"}
