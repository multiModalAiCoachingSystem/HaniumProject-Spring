# 역할: ffprobe로 영상 길이, 해상도, 코덱 같은 기본 미디어 정보를 읽습니다.
from __future__ import annotations

import json
import os
import shutil
import subprocess
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Any


class MediaInfoError(RuntimeError):
    pass


@dataclass(frozen=True)
class VideoInfo:
    durationSeconds: float
    width: int | None
    height: int | None
    fps: float | None
    sizeBytes: int | None
    videoCodec: str | None
    audioCodec: str | None

    # 함수 역할: 객체 데이터를 API 응답에 적합한 딕셔너리 형태로 변환합니다.
    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


class VideoInfoService:
    # 함수 역할: __init__에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def __init__(self, storage_root: str | Path | None = None, ffprobe_bin: str | None = None) -> None:
        self.storage_root = Path(storage_root or os.getenv("STORAGE_ROOT", "/app/storage"))
        self.ffprobe_bin = ffprobe_bin or os.getenv("FFPROBE_BIN", "ffprobe")

    # 함수 역할: resolve_path에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def resolve_path(self, file_path: str) -> Path:
        raw = Path(file_path)
        path = raw if raw.is_absolute() else self.storage_root / raw
        path = path.resolve()
        if not path.exists() or not path.is_file():
            raise MediaInfoError(f"Video file does not exist: {path}")
        return path

    # 함수 역할: probe에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def probe(self, file_path: str) -> VideoInfo:
        path = self.resolve_path(file_path)
        if shutil.which(self.ffprobe_bin) is None:
            size = path.stat().st_size
            return VideoInfo(
                durationSeconds=180.0,
                width=None,
                height=None,
                fps=None,
                sizeBytes=size,
                videoCodec=None,
                audioCodec=None,
            )

        try:
            result = subprocess.run(
                [
                    self.ffprobe_bin,
                    "-v",
                    "error",
                    "-print_format",
                    "json",
                    "-show_format",
                    "-show_streams",
                    str(path),
                ],
                check=True,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=60,
            )
            payload = json.loads(result.stdout)
        except (subprocess.SubprocessError, json.JSONDecodeError) as exc:
            raise MediaInfoError(str(exc)) from exc

        streams = payload.get("streams", [])
        fmt = payload.get("format", {})
        video_stream = next((s for s in streams if s.get("codec_type") == "video"), {})
        audio_stream = next((s for s in streams if s.get("codec_type") == "audio"), {})
        duration = self._float(video_stream.get("duration") or fmt.get("duration")) or 180.0

        return VideoInfo(
            durationSeconds=duration,
            width=self._int(video_stream.get("width")),
            height=self._int(video_stream.get("height")),
            fps=self._fps(video_stream.get("avg_frame_rate")),
            sizeBytes=self._int(fmt.get("size")),
            videoCodec=video_stream.get("codec_name"),
            audioCodec=audio_stream.get("codec_name"),
        )

    # 함수 역할: _float에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    @staticmethod
    def _float(value: Any) -> float | None:
        try:
            return round(float(value), 3)
        except (TypeError, ValueError):
            return None

    # 함수 역할: _int에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    @staticmethod
    def _int(value: Any) -> int | None:
        try:
            return int(float(value))
        except (TypeError, ValueError):
            return None

    # 함수 역할: _fps에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    @classmethod
    def _fps(cls, value: str | None) -> float | None:
        if not value or value == "0/0":
            return None
        if "/" in value:
            top, bottom = value.split("/", 1)
            denom = cls._float(bottom)
            return None if not denom else round(float(top) / denom, 3)
        return cls._float(value)
