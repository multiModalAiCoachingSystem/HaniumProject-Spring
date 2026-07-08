# 역할: 영상 메타데이터, 샘플 프레임, 오디오 파일을 준비해 LLM 분석 입력을 만듭니다.
from __future__ import annotations

from dataclasses import asdict

from app.services.ffmpeg_service import FFmpegService


class VideoSampler:
    # 함수 역할: __init__에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def __init__(self, ffmpeg_service: FFmpegService | None = None) -> None:
        self.ffmpeg = ffmpeg_service or FFmpegService()

    # 함수 역할: prepare_samples에서 영상 프레임을 추출하거나 분석에 사용할 샘플을 만듭니다.
    def prepare_samples(self, job_id: int, file_path: str, max_frames: int = 8) -> dict:
        metadata = self.ffmpeg.probe(file_path)
        frames = self.ffmpeg.sample_frames(file_path, job_id=job_id, max_frames=max_frames)
        audio = self.ffmpeg.extract_audio(file_path, job_id=job_id)

        return {
            "metadata": asdict(metadata),
            "sampledFrames": [asdict(frame) for frame in frames],
            "audio": asdict(audio) if audio else None,
        }
