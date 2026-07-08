# 역할: 영상 LLM 엔진의 provider, Ollama URL, 모델명 같은 실행 설정을 관리합니다.
from __future__ import annotations

from app.model.model_config import Settings, settings

__all__ = ["Settings", "settings"]
