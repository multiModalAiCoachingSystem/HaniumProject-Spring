# 역할: 영상 LLM 모델 설정과 로딩 관련 확장 지점을 담당합니다.
from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    storage_root: str = os.getenv("STORAGE_ROOT", "/app/storage")
    video_llm_provider: str = os.getenv("VIDEO_LLM_PROVIDER", "NONE")
    video_llm_fallback_provider: str = os.getenv("VIDEO_LLM_FALLBACK_PROVIDER", "OLLAMA")
    ollama_base_url: str = os.getenv("OLLAMA_BASE_URL", "http://host.docker.internal:11434")
    ollama_video_llm_model: str = os.getenv("OLLAMA_VIDEO_LLM_MODEL", os.getenv("VIDEO_LLM_MODEL", "qwen3-vl:4b"))
    text_llm_model: str = os.getenv("TEXT_LLM_MODEL", "qwen3:4b")
    video_llm_model: str = os.getenv("VIDEO_LLM_MODEL", "nvidia/nemotron-nano-12b-v2-vl")
    nvidia_api_base_url: str = os.getenv(
        "NVIDIA_API_BASE_URL",
        "https://integrate.api.nvidia.com/v1/chat/completions",
    )
    nvidia_api_key: str = os.getenv("NVIDIA_API_KEY", "")
    nvidia_request_timeout_seconds: int = int(os.getenv("NVIDIA_REQUEST_TIMEOUT_SECONDS", "180"))
    nvidia_max_tokens: int = int(os.getenv("NVIDIA_MAX_TOKENS", "4096"))
    nvidia_temperature: float = float(os.getenv("NVIDIA_TEMPERATURE", "0.2"))
    nvidia_top_p: float = float(os.getenv("NVIDIA_TOP_P", "1.0"))
    nvidia_max_images: int = int(os.getenv("NVIDIA_MAX_IMAGES", "8"))
    nvidia_max_payload_mb: float = float(os.getenv("NVIDIA_MAX_PAYLOAD_MB", "20"))


settings = Settings()
