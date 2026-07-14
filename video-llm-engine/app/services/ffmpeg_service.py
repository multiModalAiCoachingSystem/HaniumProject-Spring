# 역할: ffmpeg/ffprobe로 영상 정보 확인, 프레임 추출, 오디오 추출을 수행합니다.
from __future__ import annotations

import json
import os
import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Any


VIDEO_LLM_MAX_FRAMES = 16


class FFmpegError(RuntimeError):
    """Raised when ffmpeg or ffprobe cannot process a media file."""


@dataclass(frozen=True)
class VideoMetadata:
    durationSeconds: float | None
    width: int | None
    height: int | None
    fps: float | None
    frameCount: int | None
    videoCodec: str | None
    audioCodec: str | None
    formatName: str | None
    sizeBytes: int | None


@dataclass(frozen=True)
class SampledFrame:
    index: int
    timestampSeconds: float | None
    path: str


@dataclass(frozen=True)
class ExtractedAudio:
    path: str
    sampleRate: int
    channels: int


class FFmpegService:
    # 함수 역할: __init__에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def __init__(
        self,
        storage_root: str | Path | None = None,
        ffmpeg_bin: str | None = None,
        ffprobe_bin: str | None = None,
    ) -> None:
        self.storage_root = Path(storage_root or os.getenv("STORAGE_ROOT", "/app/storage"))
        self.ffmpeg_bin = ffmpeg_bin or os.getenv("FFMPEG_BIN", "ffmpeg")
        self.ffprobe_bin = ffprobe_bin or os.getenv("FFPROBE_BIN", "ffprobe")

    # 함수 역할: ensure_available에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def ensure_available(self) -> None:
        missing = [
            name
            for name, binary in (("ffmpeg", self.ffmpeg_bin), ("ffprobe", self.ffprobe_bin))
            if shutil.which(binary) is None
        ]
        if missing:
            joined = ", ".join(missing)
            raise FFmpegError(f"Required media command not found: {joined}")

    # 함수 역할: resolve_video_path에서 영상 파일 정보, 변환, 분석 요청에 필요한 처리를 수행합니다.
    def resolve_video_path(self, file_path: str) -> Path:
        raw_path = Path(file_path)
        video_path = raw_path if raw_path.is_absolute() else self.storage_root / raw_path
        video_path = video_path.resolve()

        if not video_path.exists():
            raise FFmpegError(f"Video file does not exist: {video_path}")
        if not video_path.is_file():
            raise FFmpegError(f"Video path is not a file: {video_path}")

        return video_path

    # 함수 역할: probe에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def probe(self, file_path: str | Path) -> VideoMetadata:
        self.ensure_available()
        video_path = self.resolve_video_path(str(file_path))
        result = self._run(
            [
                self.ffprobe_bin,
                "-v",
                "error",
                "-print_format",
                "json",
                "-show_format",
                "-show_streams",
                str(video_path),
            ]
        )

        try:
            payload = json.loads(result.stdout)
        except json.JSONDecodeError as exc:
            raise FFmpegError("ffprobe returned invalid JSON") from exc

        streams = payload.get("streams", [])
        fmt = payload.get("format", {})
        video_stream = next((s for s in streams if s.get("codec_type") == "video"), {})
        audio_stream = next((s for s in streams if s.get("codec_type") == "audio"), {})

        return VideoMetadata(
            durationSeconds=self._float_or_none(
                video_stream.get("duration") or fmt.get("duration")
            ),
            width=self._int_or_none(video_stream.get("width")),
            height=self._int_or_none(video_stream.get("height")),
            fps=self._parse_fps(video_stream.get("avg_frame_rate")),
            frameCount=self._int_or_none(video_stream.get("nb_frames")),
            videoCodec=video_stream.get("codec_name"),
            audioCodec=audio_stream.get("codec_name"),
            formatName=fmt.get("format_name"),
            sizeBytes=self._int_or_none(fmt.get("size")),
        )

    # 함수 역할: sample_frames에서 영상 프레임을 추출하거나 분석에 사용할 샘플을 만듭니다.
    def sample_frames(
        self,
        file_path: str | Path,
        job_id: int | str,
        max_frames: int = 16,
        width: int = 768,
    ) -> list[SampledFrame]:
        self.ensure_available()
        video_path = self.resolve_video_path(str(file_path))
        metadata = self.probe(video_path)
        output_dir = self._job_dir(job_id) / "frames"
        output_dir.mkdir(parents=True, exist_ok=True)

        max_frames = min(max(1, int(max_frames)), VIDEO_LLM_MAX_FRAMES)
        duration = metadata.durationSeconds or 0
        interval = max(duration / max_frames, 1.0)
        output_pattern = output_dir / "frame_%03d.jpg"

        self._run(
            [
                self.ffmpeg_bin,
                "-y",
                "-hide_banner",
                "-loglevel",
                "error",
                "-i",
                str(video_path),
                "-vf",
                f"fps=1/{interval:.3f},scale={width}:-2:force_original_aspect_ratio=decrease",
                "-frames:v",
                str(max_frames),
                "-q:v",
                "2",
                str(output_pattern),
            ]
        )

        frame_paths = sorted(output_dir.glob("frame_*.jpg"))
        return [
            SampledFrame(
                index=index,
                timestampSeconds=round(index * interval, 3) if duration else None,
                path=str(path),
            )
            for index, path in enumerate(frame_paths)
        ]

    # 함수 역할: extract_audio에서 음성 분석에 필요한 오디오 추출과 측정 흐름을 처리합니다.
    def extract_audio(
        self,
        file_path: str | Path,
        job_id: int | str,
        sample_rate: int = 16000,
    ) -> ExtractedAudio | None:
        self.ensure_available()
        video_path = self.resolve_video_path(str(file_path))
        output_dir = self._job_dir(job_id) / "audio"
        output_dir.mkdir(parents=True, exist_ok=True)
        output_path = output_dir / "speech.wav"

        try:
            self._run(
                [
                    self.ffmpeg_bin,
                    "-y",
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-i",
                    str(video_path),
                    "-vn",
                    "-ac",
                    "1",
                    "-ar",
                    str(sample_rate),
                    "-f",
                    "wav",
                    str(output_path),
                ]
            )
        except FFmpegError:
            return None

        return ExtractedAudio(path=str(output_path), sampleRate=sample_rate, channels=1)

    # 함수 역할: _job_dir에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def _job_dir(self, job_id: int | str) -> Path:
        return self.storage_root / "video-llm" / f"job-{job_id}"

    # 함수 역할: _run에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def _run(self, command: list[str]) -> subprocess.CompletedProcess[str]:
        try:
            return subprocess.run(
                command,
                check=True,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=180,
            )
        except subprocess.TimeoutExpired as exc:
            raise FFmpegError(f"Media command timed out: {' '.join(command[:2])}") from exc
        except subprocess.CalledProcessError as exc:
            message = exc.stderr.strip() or exc.stdout.strip() or str(exc)
            raise FFmpegError(message) from exc

    # 함수 역할: _parse_fps에서 외부 응답이나 원본 데이터를 내부에서 쓰기 좋은 구조로 해석합니다.
    @staticmethod
    def _parse_fps(value: str | None) -> float | None:
        if not value or value == "0/0":
            return None
        if "/" in value:
            numerator, denominator = value.split("/", 1)
            denom = float(denominator)
            return None if denom == 0 else round(float(numerator) / denom, 3)
        return FFmpegService._float_or_none(value)

    # 함수 역할: _float_or_none에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    @staticmethod
    def _float_or_none(value: Any) -> float | None:
        if value in (None, ""):
            return None
        try:
            return round(float(value), 3)
        except (TypeError, ValueError):
            return None

    # 함수 역할: _int_or_none에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    @staticmethod
    def _int_or_none(value: Any) -> int | None:
        if value in (None, ""):
            return None
        try:
            return int(float(value))
        except (TypeError, ValueError):
            return None
