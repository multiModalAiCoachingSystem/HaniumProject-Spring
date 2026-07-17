import math
import tempfile
import unittest
import wave
from array import array
from pathlib import Path

from app.services.audio_analyzer import AudioAnalyzer, SpeechSegment


class AudioAnalyzerTest(unittest.TestCase):
    sample_rate = 16000

    def setUp(self):
        self.analyzer = AudioAnalyzer(ffmpeg_bin="ffmpeg")

    def test_stable_voice_without_pauses_has_low_pause_ratio(self):
        path = self._write_wave([
            self._tone(2.0, 0.35),
        ])

        metrics = self.analyzer._analyze_wav_file(path)

        self.assertFalse(metrics["audioMetricsFallback"])
        self.assertEqual(metrics["pauseCount"], 0)
        self.assertLess(metrics["pauseRatio"], 0.05)
        self.assertGreater(metrics["volumeStabilityRatio"], 0.85)

    def test_long_internal_pause_increases_pause_ratio(self):
        path = self._write_wave([
            self._tone(1.0, 0.35),
            self._silence(1.0),
            self._tone(1.0, 0.35),
        ])

        metrics = self.analyzer._analyze_wav_file(path)

        self.assertFalse(metrics["audioMetricsFallback"])
        self.assertEqual(metrics["pauseCount"], 1)
        self.assertGreater(metrics["totalPauseSeconds"], 0.8)
        self.assertGreater(metrics["pauseRatio"], 0.25)

    def test_variable_volume_lowers_stability(self):
        stable = self.analyzer._analyze_wav_file(self._write_wave([
            self._tone(2.0, 0.35),
        ]))
        variable = self.analyzer._analyze_wav_file(self._write_wave([
            self._tone(0.5, 0.08),
            self._tone(0.5, 0.55),
            self._tone(0.5, 0.12),
            self._tone(0.5, 0.65),
        ]))

        self.assertFalse(variable["audioMetricsFallback"])
        self.assertLess(variable["volumeStabilityRatio"], stable["volumeStabilityRatio"])
        self.assertGreater(variable["volumeStdDevDb"], stable["volumeStdDevDb"])

    def test_silence_only_uses_fallback(self):
        path = self._write_wave([
            self._silence(2.0),
        ])

        metrics = self.analyzer._analyze_wav_file(path)

        self.assertTrue(metrics["audioMetricsFallback"])
        self.assertEqual(metrics["audioMetricsFallbackReason"], "no_speech_detected")
        self.assertEqual(metrics["pauseRatio"], 0.08)
        self.assertEqual(metrics["volumeStabilityRatio"], 0.78)

    def test_clipping_ratio_is_reported(self):
        path = self._write_wave([
            self._tone(1.2, 1.0),
        ])

        metrics = self.analyzer._analyze_wav_file(path)

        self.assertFalse(metrics["audioMetricsFallback"])
        self.assertGreater(metrics["clippingRatio"], 0.0)
        self.assertLess(metrics["volumeStabilityRatio"], 1.0)

    def test_very_low_volume_is_not_treated_as_perfectly_stable(self):
        path = self._write_wave([
            self._tone(1.5, 0.015),
        ])

        metrics = self.analyzer._analyze_wav_file(path)

        if metrics["audioMetricsFallback"]:
            self.assertEqual(metrics["audioMetricsFallbackReason"], "no_speech_detected")
        else:
            self.assertLess(metrics["meanVolumeDb"], -30.0)
            self.assertLess(metrics["volumeStabilityRatio"], 0.8)

    def test_too_short_audio_uses_fallback(self):
        path = self._write_wave([
            self._tone(0.2, 0.35),
        ])

        metrics = self.analyzer._analyze_wav_file(path)

        self.assertTrue(metrics["audioMetricsFallback"])
        self.assertEqual(metrics["audioMetricsFallbackReason"], "audio_too_short")

    def test_ratios_are_bounded(self):
        path = self._write_wave([
            self._tone(1.0, 0.25),
            self._silence(0.5),
            self._tone(1.0, 0.4),
        ])

        metrics = self.analyzer._analyze_wav_file(path)

        self.assertGreaterEqual(metrics["pauseRatio"], 0.0)
        self.assertLessEqual(metrics["pauseRatio"], 1.0)
        self.assertGreaterEqual(metrics["volumeStabilityRatio"], 0.0)
        self.assertLessEqual(metrics["volumeStabilityRatio"], 1.0)

    def test_rms_metadata_is_reported_by_default(self):
        path = self._write_wave([
            self._tone(1.2, 0.35),
        ])

        metrics = self.analyzer._analyze_wav_file(path)

        self.assertEqual(metrics["requestedVadProvider"], "RMS")
        self.assertEqual(metrics["actualVadProvider"], "RMS")
        self.assertFalse(metrics["vadFallbackUsed"])
        self.assertIsNone(metrics["vadFallbackReason"])
        self.assertGreaterEqual(metrics["speechSegmentCount"], 1)

    def test_none_provider_uses_safe_fallback(self):
        analyzer = AudioAnalyzer(ffmpeg_bin="ffmpeg", vad_provider="NONE")
        path = self._write_wave([
            self._tone(1.2, 0.35),
        ])

        metrics = analyzer._analyze_wav_file(path)

        self.assertTrue(metrics["audioMetricsFallback"])
        self.assertEqual(metrics["audioMetricsFallbackReason"], "vad_provider_none")
        self.assertEqual(metrics["requestedVadProvider"], "NONE")
        self.assertEqual(metrics["actualVadProvider"], "NONE")

    def test_silero_missing_model_falls_back_to_rms(self):
        analyzer = AudioAnalyzer(ffmpeg_bin="ffmpeg", vad_provider="SILERO", silero_model_path="")
        path = self._write_wave([
            self._tone(1.0, 0.35),
            self._silence(0.6),
            self._tone(1.0, 0.35),
        ])

        metrics = analyzer._analyze_wav_file(path)

        self.assertFalse(metrics["audioMetricsFallback"])
        self.assertEqual(metrics["requestedVadProvider"], "SILERO")
        self.assertEqual(metrics["actualVadProvider"], "RMS")
        self.assertTrue(metrics["vadFallbackUsed"])
        self.assertEqual(metrics["vadFallbackReason"], "SILERO_MODEL_PATH_MISSING")

    def test_silero_segments_drive_pause_metrics(self):
        analyzer = AudioAnalyzer(ffmpeg_bin="ffmpeg", vad_provider="SILERO")
        path = self._write_wave([
            self._tone(1.0, 0.35),
            self._silence(1.0),
            self._tone(1.0, 0.35),
        ])
        analyzer._silero_speech_segments = lambda samples, sample_rate: [
            SpeechSegment(0, sample_rate),
            SpeechSegment(sample_rate * 2, sample_rate * 3),
        ]

        metrics = analyzer._analyze_wav_file(path)

        self.assertFalse(metrics["audioMetricsFallback"])
        self.assertEqual(metrics["requestedVadProvider"], "SILERO")
        self.assertEqual(metrics["actualVadProvider"], "SILERO")
        self.assertFalse(metrics["vadFallbackUsed"])
        self.assertEqual(metrics["speechSegmentCount"], 2)
        self.assertEqual(metrics["pauseCount"], 1)
        self.assertGreater(metrics["pauseRatio"], 0.3)

    def _write_wave(self, chunks):
        samples = array("h")
        for chunk in chunks:
            samples.extend(chunk)
        handle = tempfile.NamedTemporaryFile(suffix=".wav", delete=False)
        handle.close()
        path = Path(handle.name)
        with wave.open(str(path), "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(self.sample_rate)
            wav.writeframes(samples.tobytes())
        self.addCleanup(lambda: path.exists() and path.unlink())
        return path

    def _tone(self, seconds, amplitude, frequency=440.0):
        count = int(self.sample_rate * seconds)
        samples = array("h")
        for index in range(count):
            value = math.sin(2 * math.pi * frequency * index / self.sample_rate) * amplitude
            value = max(-1.0, min(1.0, value))
            samples.append(int(value * 32767))
        return samples

    def _silence(self, seconds):
        return array("h", [0] * int(self.sample_rate * seconds))


if __name__ == "__main__":
    unittest.main()
