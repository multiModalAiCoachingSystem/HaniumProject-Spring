# 침묵

## 목적

발표 중 긴 침묵이나 잦은 끊김이 발표 흐름을 방해하는 정도를 `VOICE` 점수에 반영한다. 현재 구현 위치는 `analysis-engine/app/services/audio_analyzer.py`와 `score_calculator.py`이다.

## 입력 데이터

- `file_path`: backend가 전달한 영상 파일 경로
- `duration_seconds`: `VideoInfoService.probe()`가 읽은 영상 길이
- `pauseCount`: 유효 발화 구간 사이에서 감지된 침묵 구간 수
- `totalPauseSeconds`: pause로 인정된 침묵 시간 합계
- `averagePauseSeconds`: 평균 pause 길이
- `longestPauseSeconds`: 가장 긴 pause 길이
- `pauseRatio`: 유효 발화 범위 내 pause 시간 비율

## 오디오 전처리

`AudioAnalyzer`는 ffmpeg로 분석용 오디오를 추출한다.

```text
mono
16000 Hz
PCM 16-bit WAV
```

STT도 ffmpeg로 같은 형식의 WAV를 사용하지만, 현재 구현은 STT용 임시 WAV와 음성 metric용 임시 WAV를 각각 생성하고 분석 후 정리한다.

## 계산식

오디오는 ffmpeg로 정규화한 뒤 `AUDIO_VAD_PROVIDER`에 따라 발화 구간을 찾는다.

- `RMS`: 100ms frame의 RMS/dBFS 기반 휴리스틱을 사용한다. 현재 기본값이다.
- `SILERO`: `SILERO_VAD_MODEL_PATH`에 지정된 오프라인 Silero VAD 모델로 발화 구간을 찾는다. 모델 로딩이나 추론에 실패하면 `AUDIO_VAD_FALLBACK_PROVIDER=RMS`에 따라 RMS 방식으로 fallback한다.
- `NONE`: VAD를 사용하지 않고 안전 fallback 값을 반환한다.

RMS 방식은 전체 dB 분포의 하위 구간을 noise floor로 보고 동적 silence threshold를 만든다.

```text
rms = sqrt(mean(sample^2))
dbfs = 20 * log10(rms)
silenceThresholdDb = clamp(noiseFloor + dynamicMargin, -55, -25)
voiceFrame = dbfs >= silenceThresholdDb
```

Silero 방식은 모델이 반환한 speech segment 사이의 무음만 pause로 계산한다. 시작 전/종료 후 무음은 발표 중 pause로 계산하지 않는다. `volumeStabilityRatio`, `meanVolumeDb`, `volumeStdDevDb`, `clippingRatio`는 기존 RMS/dBFS 계산을 유지하되, Silero 사용 시에는 Silero가 검출한 발화 구간에 해당하는 frame만 대상으로 계산한다.

```text
minimumPauseSeconds = VAD_MIN_SILENCE_MS / 1000
pauseRatio = totalPauseSeconds / effectiveAudioSeconds
```

초기 환경변수 기본값:

```text
VAD_MIN_SPEECH_MS = 250
VAD_MIN_SILENCE_MS = 350
VAD_SPEECH_PAD_MS = 100
VAD_MERGE_GAP_MS = 200
SILERO_VAD_THRESHOLD = 0.5
```

## 점수화

`pauseRatio`는 별도 점수가 아니라 `VOICE` 점수에서 감점으로 반영된다.

```text
pause_penalty = min(18.0, pauseRatio * 120.0)
```

`pauseRatio`가 높을수록 `VOICE` 점수가 낮아진다. `ScoreCalculator`의 가중치와 계산식은 이번 변경에서 수정하지 않았다.

## fallback

다음 경우에는 기존 안전 기본값을 fallback으로 사용한다.

- `audio_file_missing`
- `audio_extract_failed`
- `audio_too_short`
- `no_speech_detected`
- `insufficient_voice_frames`
- `audio_decode_failed`
- `audio_metrics_error`

fallback 값:

```text
pauseRatio = 0.08
volumeStabilityRatio = 0.78
audioMetricsFallback = true
audioMetricsFallbackReason = <reason>
```

Silero 요청 후 RMS로 fallback된 경우 전체 audio metric은 실제 RMS 계산값을 사용하므로 `audioMetricsFallback=false`일 수 있다. 이때 VAD metadata로 fallback 여부를 구분한다.

```text
requestedVadProvider = SILERO
actualVadProvider = RMS
vadFallbackUsed = true
vadFallbackReason = SILERO_MODEL_PATH_MISSING 등
```

## 한계

기본값인 RMS 방식은 배경 소음이 큰 환경, 음악이 섞인 영상, 매우 작은 목소리의 영상에서는 실제 발화와 소음을 완벽하게 구분하지 못할 수 있다. Silero VAD는 이를 보완하기 위한 선택형 경로지만, 현재 Docker 이미지에는 torch와 모델 파일을 기본 포함하지 않는다. 운영에서 사용하려면 모델 파일을 저장소 볼륨 또는 이미지에 배치하고 `SILERO_VAD_MODEL_PATH`를 지정해야 한다.
