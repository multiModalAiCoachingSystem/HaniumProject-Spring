# 역할: 분석 대상 프레임을 어떤 시점에서 샘플링할지 계획합니다.
from __future__ import annotations

from pathlib import Path


class FrameExtractor:
    # 함수 역할: sample_plan에서 영상 프레임을 추출하거나 분석에 사용할 샘플을 만듭니다.
    def sample_plan(self, duration_seconds: float, max_frames: int = 8) -> list[dict]:
        if duration_seconds <= 0:
            return []
        interval = max(duration_seconds / max_frames, 1.0)
        return [
            {
                "index": index,
                "timestampSeconds": round(index * interval, 3),
                "path": None,
            }
            for index in range(max_frames)
        ]

    # 함수 역할: output_dir에서 분석 엔진에 필요한 데이터 처리 로직을 수행합니다.
    def output_dir(self, storage_root: str, job_id: int) -> Path:
        return Path(storage_root) / "analysis" / f"job-{job_id}" / "frames"
