# 테스트 시나리오

현재 저장소에는 Backend JUnit, Frontend Node test, 두 Python 엔진 unittest가 있다. 아래는 자동 테스트와 최종 수동 회귀에서 함께 유지할 핵심 시나리오다.

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
- 5초 미만 영상은 `VIDEO_TOO_SHORT`, 600초 초과 영상은 `VIDEO_TOO_LONG`
- ffprobe metadata 판독 실패는 `INVALID_VIDEO_METADATA`, 저장 파일과 DB row가 남지 않음
- 빈 파일 또는 미지원 MIME 타입 거부
- 다른 사용자의 영상 상세/스트림 접근 거부

## 분석

- `POST /api/analyses` 후 `QUEUED` job 생성
- pipeline 진행 중 `PROCESSING`과 progress 변경 확인
- 성공 시 `final_analysis_results` 생성, job `DONE`
- 엔진 오류 시 job `FAILED`, 안전한 실패 메시지 반환
- 실패 job 재시도 시 `retryCount` 증가, 기존 결과 삭제, 상태 `QUEUED`
- 취소 job 재분석 시 동일 `jobId`, 기존 결과 정리, 상태 `QUEUED`
- `QUEUED` 취소 → `CANCELED`, `PROCESSING` 취소 → `CANCEL_REQUESTED`
- 취소 확인 후 pipeline이 `DONE` 또는 `FAILED`로 덮어쓰지 않음
- 진행 중 job 재시도 시 409 `ANALYSIS_ALREADY_RUNNING`
- 같은 `videoId`의 활성 job 생성 요청 2회 시 하나만 생성되고 기존 `jobId`를 포함한 409 `ANALYSIS_ALREADY_ACTIVE`
- 동일 job 결과 저장을 반복해도 `final_analysis_results.job_id` 기준 한 row만 존재

## 결과

- 본인 결과 목록 조회
- 결과 상세의 `scores`, `feedback`, `summary` JSON 파싱 확인
- 타 사용자 결과 접근 거부
- 결과 삭제 시 job/result 및 관련 파일 정리
- `DONE`, `FAILED`, `CANCELED` 삭제 가능
- `QUEUED`, `PROCESSING`, `CANCEL_REQUESTED` 삭제 불가

## 운영 콘솔

- `USER`는 `/api/admin/**` 접근 불가
- `MANAGER`는 콘솔 조회 가능, 사용자 추방/권한 변경은 불가
- `ADMIN`은 사용자 권한 변경 가능. 대상은 `USER` 또는 `MANAGER`
- `ADMIN`/`MANAGER` 계정 추방 방지

## 프론트엔드

- `/upload`에서 업로드 후 jobId를 localStorage와 query string에 저장
- `QUEUED`/`PROCESSING`/`CANCEL_REQUESTED`일 때 4초 polling
- `DONE`일 때 결과 이동
- `FAILED`, `CANCELED`일 때 재분석 버튼 표시
- 상세 분석 지표 기본 접힘과 낮은 감지 신뢰도 경고
