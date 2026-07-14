# 역할: 영상 LLM 모델 설정과 로딩 관련 확장 지점을 담당합니다.
from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    storage_root: str = os.getenv("STORAGE_ROOT", "/app/storage")
    video_llm_provider: str = os.getenv("VIDEO_LLM_PROVIDER", "MOCK")
    ollama_base_url: str = os.getenv("OLLAMA_BASE_URL", "http://host.docker.internal:11434")
    text_llm_model: str = os.getenv("TEXT_LLM_MODEL", "qwen3:4b")
    video_llm_model: str = os.getenv("VIDEO_LLM_MODEL", "qwen3-vl:4b")


settings = Settings()
