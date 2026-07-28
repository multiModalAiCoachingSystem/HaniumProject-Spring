# 알려진 한계

## 분석 품질

- 얼굴이 작거나 측면·가림·어두움이 있으면 FaceMesh/Haar crop retry 후에도 표정과 시선 감지율이 낮을 수 있다.
- 손목과 팔꿈치가 프레임 밖에 있으면 MediaPipe Pose 기반 제스처 정확도가 낮다.
- 음성 pause와 volume stability는 RMS/dBFS 기반이며 배경 소음과 녹음 장치 자동 보정의 영향을 받는다.
- STT 품질은 음질, 발음, 전문 용어와 faster-whisper 모델 크기에 좌우된다.
- 신뢰도가 낮은 표정·시선 점수에는 상한과 fallback을 적용하지만 원본 영상 품질을 복구하지는 못한다.

## LLM

- Video LLM은 NVIDIA NIM 네트워크·서비스·quota 상태의 영향을 받으며 실패하면 Ollama, 이후 결정적 fallback 순으로 처리한다.
- Video LLM은 관찰과 설명에만 사용하고 자세·표정·시선·음성·제스처 정량 점수를 직접 변경하지 않는다.
- Text LLM `OPENAI` provider는 미구현이며 `NONE`, 잘못된 provider, Ollama 실패·timeout·JSON 파싱 실패는 결정적 한국어 fallback으로 완료한다.
- LLM 출력은 같은 영상에서도 표현이 달라질 수 있다.

## 보안과 운영

- access token은 현재 `localStorage`에 있어 XSS 공격 시 노출 위험이 있다. refresh token은 HttpOnly cookie다.
- Rate Limit은 backend 인스턴스별 메모리 방식이며 여러 인스턴스가 제한 상태를 공유하지 않는다.
- 로그인 IP 제한은 NAT 환경의 여러 사용자에게 함께 적용될 수 있다.
- 사용자별 동시 분석 개수 제한과 idempotency key는 미구현이다.
- 시스템 로그 UI는 실제 중앙 로그 저장소가 아니라 mock 데이터다.
- `/api/admin/health`의 분석 엔진 상태는 실제 health 호출이 아니라 `MOCK` 값이다.

## 복구와 확장

- Backend 재시작 복구는 조건부 상태 변경으로 중복 실행을 방지하지만 실제 backend 2개 동시 복구 검증은 남아 있다.
- 실행 중인 외부 FastAPI HTTP 요청을 backend 재시작 직후 즉시 중단하는 별도 job API는 없다.
- 공유 storage는 단일 호스트 bind mount를 전제로 한다.

## 테스트와 의존성

- CI는 실제 영상 전체 분석, 모델 다운로드, NVIDIA/Ollama 외부 호출을 수행하지 않는다.
- 브라우저 DOM/E2E 자동화는 미구현이다.
- workflow는 push 후 GitHub-hosted runner에서 최종 확인해야 한다.
- actionlint 대신 PyYAML 기반 문법 검증을 사용한다.
- Recharts `2.15.4` 유지보수가 종료되었다. 3.x는 breaking change 검토와 화면 회귀 테스트 후 별도 전환해야 한다.
- `react-router-dom 7.18.1`은 RSC action mode 관련 보안 advisory의 영향 버전이다. 현재 SPA는 RSC action을 사용하지 않지만 수정 버전이 8.3 이상이므로 major migration과 회귀 테스트가 필요하다.
- PostCSS는 취약 버전 `8.5.16`에서 안전한 패치 `8.5.23`으로 갱신했다.
- Backend Docker compile에는 `javax.annotation.meta.When` classpath 경고가 남지만 build 결과는 성공한다.
