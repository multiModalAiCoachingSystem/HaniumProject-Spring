# 아키텍처 선택 비교

## 선택한 구조

현재 구조는 `React frontend + Spring Boot backend + FastAPI analysis-engine + FastAPI video-llm-engine + MySQL + nginx`이다.

## 비교

| 선택지 | 장점 | 단점 | 현재 선택 여부 |
|---|---|---|---|
| Spring Boot 단일 서버에서 분석까지 처리 | 배포 단순 | Python ML/MediaPipe/STT 의존성 관리 어려움 | 선택하지 않음 |
| FastAPI 단일 서버에서 API와 분석 모두 처리 | Python 분석 코드 통합 쉬움 | 인증, JPA, 운영 기능 구현 부담 | 선택하지 않음 |
| Spring Boot + Python 분석 엔진 분리 | 인증/DB와 분석 책임 분리 | 서비스 간 HTTP 호출/볼륨 공유 필요 | 선택 |
| analysis-engine과 video-llm-engine 통합 | 엔진 수 감소 | 정량 분석과 모델 호출 장애/설정 분리 어려움 | 선택하지 않음 |
| nginx reverse proxy | 브라우저는 단일 origin 사용 | nginx 설정 필요 | 선택 |

## 왜 서버를 분리했나

Spring Boot는 사용자, 영상, 작업 상태, 결과 저장의 트랜잭션을 담당한다. Python 엔진은 STT, OpenCV, MediaPipe, ffmpeg, Ollama 호출처럼 Python 생태계가 강한 영역을 담당한다. 이 분리는 심사 시 "서비스 안정성과 구현 현실성"을 설명하기 좋다.

## 데이터 저장 선택

MySQL에는 핵심 상태와 최종 결과를 저장하고, 큰 영상/프레임/오디오 파일은 세 서비스가 공유하는 `/app/storage` bind mount에 둔다. 분석 결과의 세부 JSON은 `LONGTEXT` 컬럼에 저장해 기존 스키마를 유지하면서 프론트가 구조화된 결과를 표시한다.

## 현재 개선 여지

- 메시지 큐 도입 시 분석 작업 내구성과 확장성이 좋아질 수 있다.
- 각 엔진의 healthcheck와 observability를 더 강화할 수 있다.
- 결과 JSON 컬럼을 정규화하면 통계/검색이 쉬워질 수 있다.
