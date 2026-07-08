from __future__ import annotations

from app.services.audio_analyzer import AudioAnalyzer
from app.services.face_analyzer import FaceAnalyzer
from app.services.gaze_analyzer import GazeAnalyzer
from app.services.pose_analyzer import PoseAnalyzer
from app.services.score_calculator import ScoreCalculator
from app.services.stt_service import SttService
from app.services.video_info import VideoInfoService


class BasicAnalyzer:
    # 함수 역할: 하나의 발표 분석 작업에 필요한 서비스들을 초기화합니다.
    def __init__(self) -> None:
        self.video_info = VideoInfoService()
        self.audio = AudioAnalyzer()
        self.pose = PoseAnalyzer()
        self.gaze = GazeAnalyzer()
        self.face = FaceAnalyzer()
        self.score_calculator = ScoreCalculator()
        self.stt = SttService()

    # 함수 역할: 영상 정보 확인, STT 처리, 지표 추출, 점수 계산, 응답 데이터 구성 과정을 전체적으로 조정합니다.
    def analyze(self, job_id: int, video_id: int, file_path: str, transcript: str | None = None) -> dict:
        info = self.video_info.probe(file_path)
        stt_result = self.stt.from_text(transcript) if transcript else self.stt.transcribe(file_path)
        effective_transcript = (transcript or stt_result.text or "").strip()

        metrics = {}
        metrics.update(self.audio.analyze(info.durationSeconds, effective_transcript, stt_result.confidence))
        metrics.update(self.pose.analyze(info.durationSeconds, file_path))
        metrics.update(self.gaze.analyze(info.durationSeconds, file_path))
        metrics.update(self.face.analyze(info.durationSeconds, file_path))
        metrics.update(self._gesture_metrics(info.durationSeconds))

        result = self.score_calculator.calculate(metrics)
        observations = self.score_calculator.observations(result["scores"], info.durationSeconds)
        stt_payload = stt_result.to_dict()
        summary = result["summary"]
        summary["transcript"] = effective_transcript
        summary["stt"] = stt_payload

        return {
            "jobId": job_id,
            "videoId": video_id,
            "durationSeconds": info.durationSeconds,
            "videoInfo": info.to_dict(),
            "transcript": effective_transcript,
            "stt": stt_payload,
            "metrics": metrics,
            "scores": result["scores"],
            "totalScore": result["totalScore"],
            "summary": summary,
            "observations": observations,
        }

    # 함수 역할: 손 랜드마크 분석 기능이 추가되기 전까지 제스처 활동량을 임시로 추정합니다.
    @staticmethod
    def _gesture_metrics(duration_seconds: float) -> dict:
        return {
            "gestureMovementRatio": 0.46 if duration_seconds < 300 else 0.40,
            "gestureCountPerMinute": 4.2,
            "stillHandRatio": 0.18,
        }
