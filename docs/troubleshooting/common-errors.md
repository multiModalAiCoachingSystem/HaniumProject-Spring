# 자주 발생하는 오류

## 업로드가 413으로 실패

nginx `client_max_body_size`와 Spring multipart 제한을 확인한다. 현재 nginx는 500M, Spring은 `max-file-size=500MB`, `max-request-size=510MB`이다.

## 분석이 `VIDEO_FILE_NOT_FOUND`로 실패

백엔드, analysis-engine, video-llm-engine이 같은 storage 볼륨을 `/app/storage`로 공유하는지 확인한다. compose의 `HOST_STORAGE_PATH`가 모든 관련 서비스에 동일하게 마운트되어야 한다.

## ffmpeg 또는 ffprobe 오류

analysis-engine과 video-llm-engine Dockerfile은 ffmpeg를 설치한다. 로컬 실행에서는 `FFMPEG_BIN`, `FFPROBE_BIN`이 PATH에 있는 실행 파일을 가리켜야 한다.

## STT가 비어 있음

`STT_PROVIDER`, `WHISPER_MODEL`, `WHISPER_DEVICE`, `WHISPER_COMPUTE_TYPE`, `STT_LANGUAGE`를 확인한다. faster-whisper import 실패나 모델 로딩 실패 시 `stt.available=false`로 결과가 계속 진행될 수 있다.

## Video LLM 결과가 fallback으로만 나옴

`VIDEO_LLM_PROVIDER`, `NVIDIA_API_KEY`, `NVIDIA_API_BASE_URL`, `OLLAMA_BASE_URL`, `OLLAMA_VIDEO_LLM_MODEL`을 확인한다. `VIDEO_LLM_PROVIDER=NVIDIA_NIM`에서 NVIDIA 호출이 실패하면 `VIDEO_LLM_FALLBACK_PROVIDER=OLLAMA` 설정에 따라 Ollama `qwen3-vl:4b`로 fallback된다. 두 provider 모두 실패하거나 `VIDEO_LLM_PROVIDER=NONE`이면 `VIDEO_QUALITY` category의 `INFO` fallback observation이 반환된다.

## 리포트 삭제가 실패

삭제 가능한 상태는 `DONE`, `FAILED`, `CANCELED`이다. `QUEUED`, `PROCESSING`, `CANCEL_REQUESTED` 상태의 job은 삭제 API에서 409 `ANALYSIS_DELETE_NOT_ALLOWED`를 반환한다.

## 재시도 실패

재시도 가능한 상태는 `FAILED`, `CANCELED`이다. 원본 영상 파일이 storage에 없으면 409 `ANALYSIS_SOURCE_VIDEO_MISSING`가 반환된다.

## 운영 콘솔 health가 실제 엔진 상태와 다름

`GET /api/admin/health`는 현재 backend만 `UP`, 두 엔진은 `"MOCK"` 문자열을 반환한다. 실제 엔진 `/health` 호출 기반 검사는 미구현이다.
