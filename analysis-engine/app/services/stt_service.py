# 역할: 영상에서 오디오를 추출하고 Whisper 기반 STT로 발표 음성을 텍스트로 변환합니다.
from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, ClassVar

from app.services.video_info import MediaInfoError, VideoInfoService


@dataclass(frozen=True)
class SttResult:
    provider: str
    model: str
    language: str | None
    text: str
    segments: list[dict[str, Any]]
    confidence: float
    available: bool
    error: str | None = None

    # 함수 역할: 객체 데이터를 API 응답에 적합한 딕셔너리 형태로 변환합니다.
    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


class SttService:
    _MODEL_CACHE: ClassVar[dict[tuple[str, str, str], Any]] = {}

    # 함수 역할: __init__에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def __init__(
        self,
        storage_root: str | Path | None = None,
        ffmpeg_bin: str | None = None,
    ) -> None:
        self.video_info = VideoInfoService(storage_root=storage_root)
        self.ffmpeg_bin = ffmpeg_bin or os.getenv("FFMPEG_BIN", "ffmpeg")
        self.provider = os.getenv("STT_PROVIDER", "FASTER_WHISPER").upper()
        self.model_name = os.getenv("WHISPER_MODEL", "base")
        self.device = os.getenv("WHISPER_DEVICE", "cpu")
        self.compute_type = os.getenv("WHISPER_COMPUTE_TYPE", "int8")
        self.language = os.getenv("STT_LANGUAGE", "ko").strip() or None

    # 함수 역할: from_text에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def from_text(self, transcript: str | None) -> SttResult:
        text = (transcript or "").strip()
        return SttResult(
            provider="EXTERNAL",
            model="provided",
            language=self.language,
            text=text,
            segments=[],
            confidence=1.0 if text else 0.0,
            available=bool(text),
        )

    # 함수 역할: transcribe에서 발표 음성을 텍스트로 변환하는 STT 처리를 수행합니다.
    def transcribe(self, file_path: str) -> SttResult:
        if self.provider in {"OFF", "NONE", "DISABLED", "MOCK"}:
            return self._unavailable("STT is disabled by STT_PROVIDER.")
        if self.provider not in {"FASTER_WHISPER", "WHISPER"}:
            return self._unavailable(f"Unsupported STT_PROVIDER: {self.provider}")

        try:
            video_path = self.video_info.resolve_path(file_path)
            audio_path = self._extract_audio(video_path)
            model = self._load_model()
            segments_iter, info = model.transcribe(
                str(audio_path),
                language=self.language,
                vad_filter=True,
                beam_size=5,
            )
            segments = [self._segment_to_dict(segment) for segment in segments_iter]
            text = " ".join(segment["text"] for segment in segments if segment["text"]).strip()
            language = getattr(info, "language", None) or self.language
            confidence = self._confidence(segments)
            return SttResult(
                provider="FASTER_WHISPER",
                model=self.model_name,
                language=language,
                text=text,
                segments=segments,
                confidence=confidence,
                available=True,
            )
        except (ImportError, MediaInfoError, RuntimeError, subprocess.SubprocessError, OSError) as exc:
            return self._unavailable(str(exc))
        finally:
            self._cleanup_temp_audio()

    # 함수 역할: _load_model에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def _load_model(self) -> Any:
        try:
            from faster_whisper import WhisperModel
        except ImportError as exc:
            raise ImportError("faster-whisper is not installed in analysis-engine.") from exc

        key = (self.model_name, self.device, self.compute_type)
        if key not in self._MODEL_CACHE:
            self._MODEL_CACHE[key] = WhisperModel(
                self.model_name,
                device=self.device,
                compute_type=self.compute_type,
            )
        return self._MODEL_CACHE[key]

    # 함수 역할: _extract_audio에서 음성 분석에 필요한 오디오 추출과 측정 흐름을 처리합니다.
    def _extract_audio(self, video_path: Path) -> Path:
        if shutil.which(self.ffmpeg_bin) is None:
            raise RuntimeError(f"ffmpeg executable was not found: {self.ffmpeg_bin}")

        temp_dir = Path(tempfile.gettempdir()) / "mulmoco-stt"
        temp_dir.mkdir(parents=True, exist_ok=True)
        audio_path = temp_dir / f"{video_path.stem}.wav"
        subprocess.run(
            [
                self.ffmpeg_bin,
                "-y",
                "-i",
                str(video_path),
                "-vn",
                "-ac",
                "1",
                "-ar",
                "16000",
                "-f",
                "wav",
                str(audio_path),
            ],
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=180,
        )
        return audio_path

    # 함수 역할: _segment_to_dict에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    @staticmethod
    def _segment_to_dict(segment: Any) -> dict[str, Any]:
        text = getattr(segment, "text", "").strip()
        return {
            "startSeconds": round(float(getattr(segment, "start", 0.0)), 3),
            "endSeconds": round(float(getattr(segment, "end", 0.0)), 3),
            "text": text,
            "avgLogprob": round(float(getattr(segment, "avg_logprob", 0.0)), 4),
            "noSpeechProb": round(float(getattr(segment, "no_speech_prob", 0.0)), 4),
        }

    # 함수 역할: _confidence에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    @staticmethod
    def _confidence(segments: list[dict[str, Any]]) -> float:
        if not segments:
            return 0.0
        values = [1.0 - float(segment.get("noSpeechProb", 1.0)) for segment in segments]
        return round(max(0.0, min(1.0, sum(values) / len(values))), 3)

    # 함수 역할: _cleanup_temp_audio에서 음성 분석에 필요한 오디오 추출과 측정 흐름을 처리합니다.
    @staticmethod
    def _cleanup_temp_audio() -> None:
        temp_dir = Path(tempfile.gettempdir()) / "mulmoco-stt"
        if not temp_dir.exists():
            return
        for path in temp_dir.glob("*.wav"):
            try:
                path.unlink()
            except OSError:
                pass

    # 함수 역할: _unavailable에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def _unavailable(self, error: str) -> SttResult:
        return SttResult(
            provider=self.provider,
            model=self.model_name,
            language=self.language,
            text="",
            segments=[],
            confidence=0.0,
            available=False,
            error=error,
        )
