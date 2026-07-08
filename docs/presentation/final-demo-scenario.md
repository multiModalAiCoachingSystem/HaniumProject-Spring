# 최종 시연 시나리오

## 시연 목표

발표 영상을 업로드하고 분석 상태가 진행되며, 완료 후 리포트에서 점수와 타임라인 피드백을 확인하는 end-to-end 흐름을 보여준다.

## 사전 준비

- MySQL, backend, analysis-engine, video-llm-engine, frontend, nginx 실행
- Ollama 사용 시 `VIDEO_LLM_PROVIDER=OLLAMA`, `VIDEO_LLM_MODEL=qwen3-vl:4b` 설정
- 테스트용 발표 영상 준비. 500MB 이하, 지원 MIME 타입이어야 한다.

## 사용자 시나리오

1. 회원가입
2. 로그인
3. `/upload`에서 발표 영상 업로드
4. 업로드 완료 후 자동 분석 시작 확인
5. 진행률과 상태 메시지 확인
6. 완료 후 결과 보기 클릭
7. 결과 상세에서 총점, 항목별 점수, 잘한 점/개선할 점 타임라인 확인
8. 타임라인 시간을 눌러 영상 위치 이동 확인
9. `/results`에서 보관함 목록, 검색, 상태 필터 확인

## 실패/재시도 시나리오

실패 상태의 job이 있다면 `/results` 또는 `/upload?jobId={jobId}`에서 재시도 버튼을 누른다. 재시도 시 `retryCount`가 증가하고 상태가 `QUEUED`로 돌아간다.

## 운영자 시나리오

1. `ADMIN` 또는 `MANAGER` 계정으로 로그인
2. `/admin`에서 사용자 수, 분석 작업, 시스템 상태 확인
3. `ADMIN` 계정으로 사용자 권한을 `USER`/`MANAGER` 사이에서 변경
4. `ADMIN` 계정으로 일반 사용자 추방
5. 분석 작업 삭제 확인

## 시연 시 주의할 점

현재 시스템 로그 화면은 실제 서버 로그가 아니라 프론트 mock 데이터이다. Text LLM 최종 피드백과 OpenAI API 사용은 미구현이며, 실제 점수는 analysis-engine에서 계산된다.
