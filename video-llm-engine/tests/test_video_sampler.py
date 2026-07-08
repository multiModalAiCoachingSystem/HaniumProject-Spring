import unittest
import subprocess
from dataclasses import dataclass
from pathlib import Path
from tempfile import TemporaryDirectory

from app.services.ffmpeg_service import FFmpegService, VIDEO_LLM_MAX_FRAMES
from app.services.video_sampler import VideoSampler


@dataclass(frozen=True)
class FakeMetadata:
    durationSeconds: float | None = 60.0
    width: int | None = 1280
    height: int | None = 720
    fps: float | None = 30.0
    frameCount: int | None = 1800
    videoCodec: str | None = "h264"
    audioCodec: str | None = "aac"
    formatName: str | None = "mov,mp4"
    sizeBytes: int | None = 1024


@dataclass(frozen=True)
class FakeFrame:
    index: int
    timestampSeconds: float
    path: str


class FakeFFmpegService:
    def __init__(self):
        self.requested_max_frames = None

    def probe(self, file_path):
        return FakeMetadata()

    def sample_frames(self, file_path, job_id, max_frames=16):
        self.requested_max_frames = max_frames
        return [FakeFrame(index=index, timestampSeconds=float(index), path=f"frame_{index}.jpg") for index in range(max_frames)]

    def extract_audio(self, file_path, job_id):
        return None


class VideoSamplerTest(unittest.TestCase):
    def test_prepare_samples_caps_large_frame_request_for_video_llm(self):
        ffmpeg = FakeFFmpegService()
        sampler = VideoSampler(ffmpeg_service=ffmpeg)

        samples = sampler.prepare_samples(job_id=1, file_path="uploads/video.mp4", max_frames=768)

        self.assertEqual(ffmpeg.requested_max_frames, VIDEO_LLM_MAX_FRAMES)
        self.assertEqual(len(samples["sampledFrames"]), VIDEO_LLM_MAX_FRAMES)


class FakeDirectFFmpegService(FFmpegService):
    def __init__(self, storage_root):
        super().__init__(storage_root=storage_root, ffmpeg_bin="ffmpeg", ffprobe_bin="ffprobe")
        self.last_command = None

    def ensure_available(self):
        return None

    def resolve_video_path(self, file_path):
        return Path(file_path)

    def probe(self, file_path):
        return FakeMetadata()

    def _run(self, command):
        self.last_command = command
        return subprocess.CompletedProcess(command, 0, "", "")


class FFmpegServiceTest(unittest.TestCase):
    def test_sample_frames_caps_large_frame_request_before_ffmpeg_command(self):
        with TemporaryDirectory() as temp_dir:
            service = FakeDirectFFmpegService(storage_root=temp_dir)

            service.sample_frames("video.mp4", job_id=1, max_frames=768)

            frame_arg_index = service.last_command.index("-frames:v") + 1
            self.assertEqual(service.last_command[frame_arg_index], str(VIDEO_LLM_MAX_FRAMES))


if __name__ == "__main__":
    unittest.main()
