from __future__ import annotations

import logging
import math
import os
import re
import shutil
import subprocess
import tempfile
import threading
import time
import wave
from array import array
from dataclasses import dataclass
from pathlib import Path
from statistics import mean, pstdev
from typing import Any


logger = logging.getLogger("uvicorn.error")


class VadProviderError(RuntimeError):
    def __init__(self, reason: str) -> None:
        super().__init__(reason)
        self.reason = reason


@dataclass(frozen=True)
class SpeechSegment:
    start_sample: int
    end_sample: int

    @property
    def sample_count(self) -> int:
        return max(0, self.end_sample - self.start_sample)


class SileroVadModelCache:
    _lock = threading.Lock()
    _inference_lock = threading.Lock()
    _model: Any | None = None
    _torch: Any | None = None
    _model_path: str | None = None

    @classmethod
    def load(cls, model_path: str | None) -> tuple[Any, Any]:
        if not model_path:
            raise VadProviderError("SILERO_MODEL_PATH_MISSING")

        path = Path(model_path)
        if not path.exists() or not path.is_file():
            raise VadProviderError("SILERO_MODEL_FILE_MISSING")

        normalized_path = str(path.resolve())
        with cls._lock:
            if cls._model is not None and cls._model_path == normalized_path:
                return cls._model, cls._torch

            try:
                import torch  # type: ignore
            except ImportError as exc:
                raise VadProviderError("SILERO_TORCH_IMPORT_FAILED") from exc

            try:
                model = torch.jit.load(normalized_path, map_location="cpu")
                model.eval()
            except Exception as exc:  # pragma: no cover - depends on external model file
                raise VadProviderError("SILERO_MODEL_LOAD_FAILED") from exc

            cls._model = model
            cls._torch = torch
            cls._model_path = normalized_path
            return model, torch

    @classmethod
    def inference_lock(cls) -> threading.Lock:
        return cls._inference_lock


class AudioAnalyzer:
    FILLERS = ["음", "어", "그", "약간", "뭔가", "이제", "저", "그러니까", "그런가"]
    SAMPLE_RATE = 16000
    FRAME_MS = 100
    MIN_AUDIO_SECONDS = 1.0
    MIN_VOICE_SECONDS = 0.8
    MIN_PAUSE_SECONDS = 0.35

    def __init__(
        self,
        ffmpeg_bin: str | None = None,
        vad_provider: str | None = None,
        vad_fallback_provider: str | None = None,
        silero_model_path: str | None = None,
    ) -> None:
        self.ffmpeg_bin = ffmpeg_bin or os.getenv("FFMPEG_BIN", "ffmpeg")
        self.vad_provider = self._provider(vad_provider or os.getenv("AUDIO_VAD_PROVIDER", "RMS"))
        self.vad_fallback_provider = self._provider(
            vad_fallback_provider or os.getenv("AUDIO_VAD_FALLBACK_PROVIDER", "RMS")
        )
        self.vad_min_speech_ms = self._env_int("VAD_MIN_SPEECH_MS", 250)
        self.vad_min_silence_ms = self._env_int("VAD_MIN_SILENCE_MS", 350)
        self.vad_speech_pad_ms = self._env_int("VAD_SPEECH_PAD_MS", 100)
        self.vad_merge_gap_ms = self._env_int("VAD_MERGE_GAP_MS", 200)
        self.silero_threshold = self._env_float("SILERO_VAD_THRESHOLD", 0.5)
        self.silero_model_path = silero_model_path if silero_model_path is not None else os.getenv("SILERO_VAD_MODEL_PATH")

    def analyze(
        self,
        duration_seconds: float,
        transcript: str | None = None,
        stt_confidence: float = 0.0,
        file_path: str | None = None,
    ) -> dict[str, Any]:
        transcript = (transcript or "").strip()
        text_metrics = self._text_metrics(duration_seconds, transcript, stt_confidence)
        signal_metrics = self._signal_metrics(file_path, duration_seconds)
        return {**text_metrics, **signal_metrics}

    def _text_metrics(self, duration_seconds: float, transcript: str, stt_confidence: float) -> dict[str, Any]:
        syllable_count = self._estimate_korean_syllables(transcript)
        token_count = len(re.findall(r"\S+", transcript))

        if syllable_count == 0 and token_count == 0:
            speech_speed = 285.0
            filler_ratio = 0.015
        else:
            speech_units = syllable_count or token_count
            speech_speed = round(speech_units / max(duration_seconds / 60.0, 0.1), 1)
            filler_ratio = 0.0

        filler_count = sum(transcript.count(word) for word in self.FILLERS)
        if syllable_count > 0 or token_count > 0:
            denominator = max(syllable_count or token_count, 1)
            filler_ratio = round(filler_count / denominator, 4)

        return {
            "speechSpeedSpm": speech_speed,
            "fillerWordCount": filler_count,
            "fillerWordRatio": filler_ratio,
            "sttConfidence": round(max(0.0, min(1.0, stt_confidence)), 3),
        }

    def _signal_metrics(self, file_path: str | None, duration_seconds: float) -> dict[str, Any]:
        if not file_path:
            return self._with_vad_metadata(self._fallback(duration_seconds, "audio_file_missing"), self.vad_provider, "NONE")
        source = Path(file_path)
        if not source.exists() or not source.is_file():
            return self._with_vad_metadata(self._fallback(duration_seconds, "audio_file_missing"), self.vad_provider, "NONE")

        try:
            wav_path = self._extract_audio(source)
            metrics = self._analyze_wav_file(wav_path)
            return metrics
        except subprocess.SubprocessError:
            return self._with_vad_metadata(self._fallback(duration_seconds, "audio_extract_failed"), self.vad_provider, "NONE")
        except wave.Error:
            return self._with_vad_metadata(self._fallback(duration_seconds, "audio_decode_failed"), self.vad_provider, "NONE")
        except (OSError, ValueError, OverflowError) as exc:
            logger.warning("Audio metrics fallback. reason=audio_metrics_error error=%s", exc.__class__.__name__)
            return self._with_vad_metadata(self._fallback(duration_seconds, "audio_metrics_error"), self.vad_provider, "NONE")
        finally:
            self._cleanup_temp_audio()

    def _extract_audio(self, video_path: Path) -> Path:
        if shutil.which(self.ffmpeg_bin) is None:
            raise subprocess.SubprocessError("ffmpeg_not_found")

        temp_dir = Path(tempfile.gettempdir()) / "mulmoco-audio-metrics"
        temp_dir.mkdir(parents=True, exist_ok=True)
        audio_path = temp_dir / f"{video_path.stem}-metrics.wav"
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
                str(self.SAMPLE_RATE),
                "-acodec",
                "pcm_s16le",
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

    def _analyze_wav_file(self, wav_path: Path) -> dict[str, Any]:
        started_at = time.perf_counter()
        samples, sample_rate = self._read_pcm16_mono(wav_path)
        if not samples:
            return self._with_vad_metadata(
                self._fallback(0.0, "audio_too_short"),
                self.vad_provider,
                "NONE",
                processing_time_ms=self._elapsed_ms(started_at),
            )

        duration_seconds = len(samples) / sample_rate
        if duration_seconds < self.MIN_AUDIO_SECONDS:
            return self._with_vad_metadata(
                self._fallback(duration_seconds, "audio_too_short"),
                self.vad_provider,
                "NONE",
                processing_time_ms=self._elapsed_ms(started_at),
            )

        frame_size = max(1, int(sample_rate * self.FRAME_MS / 1000))
        frame_duration = frame_size / sample_rate
        rms_values = self._frame_rms(samples, frame_size)
        if not rms_values or not all(math.isfinite(value) for value in rms_values):
            return self._with_vad_metadata(
                self._fallback(duration_seconds, "audio_decode_failed"),
                self.vad_provider,
                "NONE",
                processing_time_ms=self._elapsed_ms(started_at),
            )

        db_values = [self._rms_to_dbfs(value) for value in rms_values]

        if self.vad_provider == "NONE":
            return self._with_vad_metadata(
                self._fallback(duration_seconds, "vad_provider_none"),
                "NONE",
                "NONE",
                processing_time_ms=self._elapsed_ms(started_at),
            )

        if self.vad_provider == "SILERO":
            try:
                speech_segments = self._silero_speech_segments(samples, sample_rate)
                metrics = self._metrics_from_segments(
                    samples,
                    sample_rate,
                    db_values,
                    frame_duration,
                    speech_segments,
                )
                return self._with_vad_metadata(
                    metrics,
                    "SILERO",
                    "SILERO",
                    speech_segment_count=len(speech_segments),
                    processing_time_ms=self._elapsed_ms(started_at),
                )
            except VadProviderError as exc:
                if self.vad_fallback_provider == "NONE":
                    return self._with_vad_metadata(
                        self._fallback(duration_seconds, exc.reason),
                        "SILERO",
                        "NONE",
                        vad_fallback_used=True,
                        vad_fallback_reason=exc.reason,
                        processing_time_ms=self._elapsed_ms(started_at),
                    )

                rms_metrics = self._rms_metrics(samples, sample_rate, db_values, frame_duration)
                return self._with_vad_metadata(
                    rms_metrics,
                    "SILERO",
                    "RMS",
                    vad_fallback_used=True,
                    vad_fallback_reason=exc.reason,
                    speech_segment_count=int(rms_metrics.get("speechSegmentCount") or 0),
                    processing_time_ms=self._elapsed_ms(started_at),
                )

        metrics = self._rms_metrics(samples, sample_rate, db_values, frame_duration)
        return self._with_vad_metadata(
            metrics,
            "RMS",
            "RMS",
            speech_segment_count=int(metrics.get("speechSegmentCount") or 0),
            processing_time_ms=self._elapsed_ms(started_at),
        )

    def _rms_metrics(
        self,
        samples: list[int],
        sample_rate: int,
        db_values: list[float],
        frame_duration: float,
    ) -> dict[str, Any]:
        duration_seconds = len(samples) / sample_rate
        threshold_db = self._silence_threshold(db_values)
        voice_flags = [db >= threshold_db for db in db_values]
        voice_indices = [index for index, flag in enumerate(voice_flags) if flag]
        speech_seconds = round(len(voice_indices) * frame_duration, 3)
        if speech_seconds < self.MIN_VOICE_SECONDS:
            return self._fallback(duration_seconds, "no_speech_detected")

        first_voice = voice_indices[0]
        last_voice = voice_indices[-1]
        voice_db = [db_values[index] for index in voice_indices]
        if len(voice_db) < 3:
            return self._fallback(duration_seconds, "insufficient_voice_frames")

        pauses = self._pause_segments(voice_flags, first_voice, last_voice, frame_duration)
        speech_segment_count = len(self._segments_from_voice_flags(voice_flags, first_voice, last_voice))
        total_pause_seconds = round(sum(pauses), 3)
        effective_duration = max((last_voice - first_voice + 1) * frame_duration, frame_duration)
        pause_ratio = self._clamp(total_pause_seconds / effective_duration)

        mean_db = mean(voice_db)
        std_db = pstdev(voice_db) if len(voice_db) > 1 else 0.0
        sorted_voice_db = sorted(voice_db)
        low = self._percentile(sorted_voice_db, 0.05)
        high = self._percentile(sorted_voice_db, 0.95)
        range_db = max(0.0, high - low)
        clipping_ratio = self._clipping_ratio(samples)
        volume_stability = self._volume_stability(mean_db, std_db, range_db, clipping_ratio)

        return {
            "pauseCount": len(pauses),
            "totalPauseSeconds": total_pause_seconds,
            "averagePauseSeconds": round(total_pause_seconds / len(pauses), 3) if pauses else 0.0,
            "longestPauseSeconds": round(max(pauses), 3) if pauses else 0.0,
            "pauseRatio": round(pause_ratio, 3),
            "speechSeconds": speech_seconds,
            "effectiveAudioSeconds": round(effective_duration, 3),
            "meanVolumeDb": round(mean_db, 1),
            "volumeStdDevDb": round(std_db, 1),
            "volumeRangeDb": round(range_db, 1),
            "clippingRatio": round(clipping_ratio, 4),
            "volumeStabilityRatio": round(volume_stability, 3),
            "silenceThresholdDb": round(threshold_db, 1),
            "speechSegmentCount": speech_segment_count,
            "audioMetricsFallback": False,
            "audioMetricsFallbackReason": None,
        }

    def _metrics_from_segments(
        self,
        samples: list[int],
        sample_rate: int,
        db_values: list[float],
        frame_duration: float,
        speech_segments: list[SpeechSegment],
    ) -> dict[str, Any]:
        if not speech_segments:
            raise VadProviderError("SILERO_NO_SPEECH")

        duration_seconds = len(samples) / sample_rate
        speech_seconds = round(sum(segment.sample_count for segment in speech_segments) / sample_rate, 3)
        if speech_seconds < self.MIN_VOICE_SECONDS:
            raise VadProviderError("SILERO_SPEECH_TOO_SHORT")

        first_sample = speech_segments[0].start_sample
        last_sample = speech_segments[-1].end_sample
        effective_duration = max((last_sample - first_sample) / sample_rate, frame_duration)
        pauses = self._pauses_between_segments(speech_segments, sample_rate)
        total_pause_seconds = round(sum(pauses), 3)
        pause_ratio = self._clamp(total_pause_seconds / effective_duration)
        voice_frame_indices = self._frame_indices_in_segments(db_values, frame_duration, sample_rate, speech_segments)
        if len(voice_frame_indices) < 3:
            raise VadProviderError("SILERO_INSUFFICIENT_VOICE_FRAMES")

        voice_db = [db_values[index] for index in voice_frame_indices]
        mean_db = mean(voice_db)
        std_db = pstdev(voice_db) if len(voice_db) > 1 else 0.0
        sorted_voice_db = sorted(voice_db)
        low = self._percentile(sorted_voice_db, 0.05)
        high = self._percentile(sorted_voice_db, 0.95)
        range_db = max(0.0, high - low)
        clipping_ratio = self._clipping_ratio(samples)
        volume_stability = self._volume_stability(mean_db, std_db, range_db, clipping_ratio)
        silence_threshold = self._silence_threshold(db_values)

        return {
            "pauseCount": len(pauses),
            "totalPauseSeconds": total_pause_seconds,
            "averagePauseSeconds": round(total_pause_seconds / len(pauses), 3) if pauses else 0.0,
            "longestPauseSeconds": round(max(pauses), 3) if pauses else 0.0,
            "pauseRatio": round(pause_ratio, 3),
            "speechSeconds": speech_seconds,
            "effectiveAudioSeconds": round(effective_duration, 3),
            "meanVolumeDb": round(mean_db, 1),
            "volumeStdDevDb": round(std_db, 1),
            "volumeRangeDb": round(range_db, 1),
            "clippingRatio": round(clipping_ratio, 4),
            "volumeStabilityRatio": round(volume_stability, 3),
            "silenceThresholdDb": round(silence_threshold, 1),
            "speechSegmentCount": len(speech_segments),
            "audioMetricsFallback": False,
            "audioMetricsFallbackReason": None,
        }

    def _silero_speech_segments(self, samples: list[int], sample_rate: int) -> list[SpeechSegment]:
        if sample_rate != self.SAMPLE_RATE:
            raise VadProviderError("SILERO_UNSUPPORTED_SAMPLE_RATE")
        if not samples:
            raise VadProviderError("SILERO_EMPTY_AUDIO")

        model, torch = SileroVadModelCache.load(self.silero_model_path)
        waveform = self._normalized_waveform(samples)
        if not waveform:
            raise VadProviderError("SILERO_INVALID_WAVEFORM")

        probabilities = []
        chunk_size = 512
        try:
            with SileroVadModelCache.inference_lock():
                if hasattr(model, "reset_states"):
                    model.reset_states()
                with torch.inference_mode():
                    for start in range(0, len(waveform), chunk_size):
                        chunk = waveform[start : start + chunk_size]
                        if len(chunk) < chunk_size:
                            chunk = chunk + [0.0] * (chunk_size - len(chunk))
                        tensor = torch.tensor(chunk, dtype=torch.float32)
                        probabilities.append(self._silero_probability(model, torch, tensor, sample_rate))
        except Exception as exc:  # pragma: no cover - depends on external model file
            raise VadProviderError("SILERO_INFERENCE_FAILED") from exc

        segments = self._probabilities_to_segments(probabilities, chunk_size, len(waveform))
        if not segments:
            raise VadProviderError("SILERO_NO_SPEECH")
        return segments

    @staticmethod
    def _silero_probability(model: Any, torch: Any, tensor: Any, sample_rate: int) -> float:
        try:
            output = model(tensor, sample_rate)
        except (TypeError, RuntimeError):
            output = model(tensor.unsqueeze(0), sample_rate)
        if hasattr(output, "detach"):
            output = output.detach().cpu()
        if hasattr(output, "flatten"):
            output = output.flatten()[0]
        if hasattr(output, "item"):
            return float(output.item())
        return float(output)

    def _probabilities_to_segments(
        self,
        probabilities: list[float],
        chunk_size: int,
        total_samples: int,
    ) -> list[SpeechSegment]:
        min_speech_samples = int(self.SAMPLE_RATE * self.vad_min_speech_ms / 1000)
        min_silence_chunks = max(1, int(math.ceil(self.vad_min_silence_ms / (chunk_size / self.SAMPLE_RATE * 1000))))
        pad_samples = int(self.SAMPLE_RATE * self.vad_speech_pad_ms / 1000)

        segments: list[SpeechSegment] = []
        in_speech = False
        speech_start = 0
        silence_count = 0

        for index, probability in enumerate(probabilities):
            is_speech = probability >= self.silero_threshold
            chunk_start = index * chunk_size
            chunk_end = min(total_samples, chunk_start + chunk_size)
            if is_speech:
                if not in_speech:
                    speech_start = max(0, chunk_start - pad_samples)
                    in_speech = True
                silence_count = 0
                continue

            if not in_speech:
                continue
            silence_count += 1
            if silence_count >= min_silence_chunks:
                speech_end = max(speech_start, chunk_end - silence_count * chunk_size + pad_samples)
                if speech_end - speech_start >= min_speech_samples:
                    segments.append(SpeechSegment(speech_start, min(total_samples, speech_end)))
                in_speech = False
                silence_count = 0

        if in_speech:
            speech_end = total_samples
            if speech_end - speech_start >= min_speech_samples:
                segments.append(SpeechSegment(speech_start, speech_end))

        return self._merge_segments(segments, total_samples)

    def _merge_segments(self, segments: list[SpeechSegment], total_samples: int) -> list[SpeechSegment]:
        if not segments:
            return []
        merge_gap_samples = int(self.SAMPLE_RATE * self.vad_merge_gap_ms / 1000)
        merged = [segments[0]]
        for segment in segments[1:]:
            previous = merged[-1]
            if segment.start_sample - previous.end_sample <= merge_gap_samples:
                merged[-1] = SpeechSegment(previous.start_sample, min(total_samples, max(previous.end_sample, segment.end_sample)))
            else:
                merged.append(segment)
        return merged

    def _read_pcm16_mono(self, wav_path: Path) -> tuple[list[int], int]:
        with wave.open(str(wav_path), "rb") as wav:
            channels = wav.getnchannels()
            sample_width = wav.getsampwidth()
            sample_rate = wav.getframerate()
            frame_count = wav.getnframes()
            if channels != 1 or sample_width != 2 or sample_rate <= 0 or frame_count <= 0:
                raise wave.Error("unsupported_wav_format")
            raw = wav.readframes(frame_count)

        values = array("h")
        values.frombytes(raw)
        if values.itemsize != 2:
            raise wave.Error("unsupported_sample_width")
        return list(values), sample_rate

    def _frame_rms(self, samples: list[int], frame_size: int) -> list[float]:
        rms_values = []
        max_value = 32768.0
        for start in range(0, len(samples), frame_size):
            frame = samples[start : start + frame_size]
            if len(frame) < frame_size // 2:
                continue
            square_mean = sum((sample / max_value) ** 2 for sample in frame) / len(frame)
            rms_values.append(math.sqrt(square_mean))
        return rms_values

    @staticmethod
    def _rms_to_dbfs(rms: float) -> float:
        if rms <= 1e-8:
            return -80.0
        return max(-80.0, min(0.0, 20.0 * math.log10(rms)))

    def _silence_threshold(self, db_values: list[float]) -> float:
        sorted_db = sorted(db_values)
        noise_floor = self._percentile(sorted_db, 0.20)
        median_db = self._percentile(sorted_db, 0.50)
        return max(-55.0, min(-25.0, noise_floor + max(6.0, (median_db - noise_floor) * 0.45)))

    def _pause_segments(
        self,
        voice_flags: list[bool],
        first_voice: int,
        last_voice: int,
        frame_duration: float,
    ) -> list[float]:
        pauses = []
        current = 0
        min_pause_seconds = self.vad_min_silence_ms / 1000.0
        for flag in voice_flags[first_voice : last_voice + 1]:
            if not flag:
                current += 1
                continue
            if current > 0:
                seconds = current * frame_duration
                if seconds >= min_pause_seconds:
                    pauses.append(seconds)
                current = 0
        if current > 0:
            seconds = current * frame_duration
            if seconds >= min_pause_seconds:
                pauses.append(seconds)
        return pauses

    def _segments_from_voice_flags(
        self,
        voice_flags: list[bool],
        first_voice: int,
        last_voice: int,
    ) -> list[tuple[int, int]]:
        segments = []
        start = None
        for index in range(first_voice, last_voice + 1):
            if voice_flags[index]:
                if start is None:
                    start = index
                continue
            if start is not None:
                segments.append((start, index))
                start = None
        if start is not None:
            segments.append((start, last_voice + 1))
        return segments

    def _pauses_between_segments(self, speech_segments: list[SpeechSegment], sample_rate: int) -> list[float]:
        pauses = []
        min_pause_seconds = self.vad_min_silence_ms / 1000.0
        for previous, current in zip(speech_segments, speech_segments[1:]):
            gap = max(0.0, (current.start_sample - previous.end_sample) / sample_rate)
            if gap >= min_pause_seconds:
                pauses.append(gap)
        return pauses

    @staticmethod
    def _frame_indices_in_segments(
        db_values: list[float],
        frame_duration: float,
        sample_rate: int,
        speech_segments: list[SpeechSegment],
    ) -> list[int]:
        indices = []
        cursor = 0
        for index in range(len(db_values)):
            center_sample = int((index + 0.5) * frame_duration * sample_rate)
            while cursor < len(speech_segments) and center_sample >= speech_segments[cursor].end_sample:
                cursor += 1
            if cursor >= len(speech_segments):
                break
            segment = speech_segments[cursor]
            if segment.start_sample <= center_sample < segment.end_sample:
                indices.append(index)
        return indices

    @staticmethod
    def _normalized_waveform(samples: list[int]) -> list[float]:
        waveform = []
        for sample in samples:
            value = sample / 32768.0
            if not math.isfinite(value):
                continue
            waveform.append(max(-1.0, min(1.0, value)))
        return waveform

    @staticmethod
    def _clipping_ratio(samples: list[int]) -> float:
        if not samples:
            return 0.0
        clipped = sum(1 for sample in samples if abs(sample) >= 32112)
        return max(0.0, min(1.0, clipped / len(samples)))

    def _volume_stability(self, mean_db: float, std_db: float, range_db: float, clipping_ratio: float) -> float:
        std_penalty = min(0.65, std_db / 18.0)
        range_penalty = min(0.25, range_db / 80.0)
        clipping_penalty = min(0.25, clipping_ratio * 5.0)
        quiet_penalty = 0.0
        if mean_db < -30.0:
            quiet_penalty = min(0.35, (-30.0 - mean_db) / 35.0)
        return round(self._clamp(1.0 - std_penalty - range_penalty - clipping_penalty - quiet_penalty), 3)

    def _fallback(self, duration_seconds: float, reason: str) -> dict[str, Any]:
        return {
            "pauseCount": max(1, int(duration_seconds // 35)) if duration_seconds > 0 else 0,
            "totalPauseSeconds": round(max(duration_seconds, 0.0) * 0.08, 3),
            "averagePauseSeconds": 0.0,
            "longestPauseSeconds": 0.0,
            "pauseRatio": 0.08,
            "speechSeconds": 0.0,
            "effectiveAudioSeconds": round(max(duration_seconds, 0.0), 3),
            "meanVolumeDb": None,
            "volumeStdDevDb": None,
            "volumeRangeDb": None,
            "clippingRatio": 0.0,
            "volumeStabilityRatio": 0.78,
            "silenceThresholdDb": None,
            "speechSegmentCount": 0,
            "audioMetricsFallback": True,
            "audioMetricsFallbackReason": reason,
        }

    def _with_vad_metadata(
        self,
        metrics: dict[str, Any],
        requested_provider: str,
        actual_provider: str,
        vad_fallback_used: bool = False,
        vad_fallback_reason: str | None = None,
        speech_segment_count: int | None = None,
        processing_time_ms: float | None = None,
    ) -> dict[str, Any]:
        metrics["requestedVadProvider"] = requested_provider
        metrics["actualVadProvider"] = actual_provider
        metrics["vadFallbackUsed"] = vad_fallback_used
        metrics["vadFallbackReason"] = vad_fallback_reason
        metrics["speechSegmentCount"] = int(speech_segment_count if speech_segment_count is not None else metrics.get("speechSegmentCount") or 0)
        if processing_time_ms is not None:
            metrics["audioVadProcessingTimeMs"] = round(processing_time_ms, 1)
        return metrics

    @staticmethod
    def _elapsed_ms(started_at: float) -> float:
        return (time.perf_counter() - started_at) * 1000.0

    @staticmethod
    def _provider(value: str | None) -> str:
        normalized = (value or "RMS").strip().upper()
        if normalized in {"SILERO", "RMS", "NONE"}:
            return normalized
        return "RMS"

    @staticmethod
    def _env_int(name: str, default: int) -> int:
        try:
            return int(os.getenv(name, str(default)))
        except (TypeError, ValueError):
            return default

    @staticmethod
    def _env_float(name: str, default: float) -> float:
        try:
            return float(os.getenv(name, str(default)))
        except (TypeError, ValueError):
            return default

    @staticmethod
    def _percentile(sorted_values: list[float], ratio: float) -> float:
        if not sorted_values:
            return 0.0
        index = max(0, min(len(sorted_values) - 1, int(round((len(sorted_values) - 1) * ratio))))
        return sorted_values[index]

    @staticmethod
    def _clamp(value: float, low: float = 0.0, high: float = 1.0) -> float:
        return max(low, min(high, value))

    @staticmethod
    def _estimate_korean_syllables(text: str) -> int:
        return sum(1 for char in text if "가" <= char <= "힣")

    @staticmethod
    def _cleanup_temp_audio() -> None:
        temp_dir = Path(tempfile.gettempdir()) / "mulmoco-audio-metrics"
        if not temp_dir.exists():
            return
        for path in temp_dir.glob("*.wav"):
            try:
                path.unlink()
            except OSError:
                pass
