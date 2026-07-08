# 테스트 시나리오

현재 저장소에서 확인한 자동 테스트 파일은 없다. 아래는 실제 구현 기준으로 수동/자동 테스트를 작성할 때 필요한 시나리오이다.

## 인증

- 회원가입 성공: 이메일, 비밀번호, 비밀번호 확인, 닉네임 입력
- 회원가입 실패: 중복 이메일, 비밀번호 확인 불일치, 8자 미만 비밀번호
- 로그인 성공: access token 응답, refresh cookie 설정
- 로그인 실패: 잘못된 이메일/비밀번호
- `/api/auth/me`: 토큰 없으면 실패, 토큰 있으면 사용자 정보 반환
- 로그아웃: refresh token revoke 및 cookie 만료

## 영상 업로드

- 지원 MIME 타입 업로드 성공: mp4, quicktime, avi, matroska, webm
- 500MB 초과 파일 거부
- 빈 파일 또는 미지원 MIME 타입 거부
- 다른 사용자의 영상 상세/스트림 접근 거부

## 분석

- `POST /api/analyses` 후 `QUEUED` job 생성
- pipeline 진행 중 `PROCESSING`과 progress 변경 확인
- 성공 시 `final_analysis_results` 생성, job `DONE`
- 엔진 오류 시 job `FAILED`, 안전한 실패 메시지 반환
- 실패 job 재시도 시 `retryCount` 증가, 기존 결과 삭제, 상태 `QUEUED`
- 진행 중 job 재시도 시 409 `ANALYSIS_ALREADY_RUNNING`

## 결과

- 본인 결과 목록 조회
- 결과 상세의 `scores`, `feedback`, `summary` JSON 파싱 확인
- 타 사용자 결과 접근 거부
- 결과 삭제 시 job/result 및 관련 파일 정리

## 운영 콘솔

- `USER`는 `/api/admin/**` 접근 불가
- `MANAGER`는 콘솔 조회 가능, 사용자 추방/권한 변경은 불가
- `ADMIN`은 사용자 권한 변경 가능. 대상은 `USER` 또는 `MANAGER`
- `ADMIN`/`MANAGER` 계정 추방 방지

## 프론트엔드

- `/upload`에서 업로드 후 jobId를 localStorage와 query string에 저장
- `QUEUED`/`PROCESSING`일 때 4초 polling
- `DONE`일 때 결과 이동
- `FAILED`일 때 재시도 버튼 표시
