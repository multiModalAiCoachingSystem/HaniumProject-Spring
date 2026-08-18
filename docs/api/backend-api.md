# Backend API

공통 base URL은 `/api`이다. 프론트엔드 `frontend/src/api/httpClient.js`는 `baseURL: "/api"`와 `Authorization: Bearer <accessToken>` 헤더를 사용한다. 응답 래퍼는 `ApiResponse<T>`이며 구조는 `docs/api/response-format.md`를 따른다.

## Rate Limit

| 대상 | 기본 제한 | 기준 |
|---|---:|---|
| 로그인 | 분당 10회 | IP |
| 회원가입 | 시간당 5회 | IP |
| token refresh | 분당 20회 | IP |
| 영상 업로드 | 시간당 20회 | 사용자, 미인증 시 IP |
| 분석 시작 | 시간당 20회 | 사용자 |
| 재시도 | 시간당 10회 | 사용자 |
| 관리자 API | 분당 60회 | principal |
| 그 외 API | 분당 120회 | principal 또는 IP |

값은 `infra/docker/.env.example`의 `RATE_LIMIT_*` 변수로 조정한다. 초과하면 HTTP 429와 `RATE_LIMIT_EXCEEDED`를 반환한다. `OPTIONS`와 `/api/health`는 제한 대상이 아니다.

## 인증 API

| Method | URL | 인증 | Controller | Service | 소유권 검증 |
|---|---|---|---|---|---|
| POST | `/api/auth/signup` | 불필요 | `AuthController.signup` | `AuthService.signup` | 해당 없음 |
| POST | `/api/auth/login` | 불필요 | `AuthController.login` | `AuthService.login` | 해당 없음 |
| POST | `/api/auth/refresh` | refresh cookie | `AuthController.refresh` | `AuthService.refresh` | refresh token 해시 검증 |
| POST | `/api/auth/logout` | 필요 | `AuthController.logout` | `AuthService.logout` | refresh token 해시 검증 |
| GET | `/api/auth/me` | 필요 | `AuthController.me` | `AuthService.me` | 현재 JWT 사용자 |

### POST `/api/auth/signup`

요청 JSON:

```json
{
  "email": "user@example.com",
  "password": "password123",
  "passwordConfirm": "password123",
  "nickname": "발표자"
}
```

성공 응답 예시:

```json
{
  "success": true,
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "nickname": "발표자",
    "role": "USER",
    "createdAt": "2026-07-07T19:00:00+09:00"
  },
  "message": "회원가입이 완료되었습니다.",
  "errorCode": null
}
```

오류 상태 코드: 400 `VALIDATION_ERROR`, `EMAIL_ALREADY_EXISTS`, `PASSWORD_CONFIRM_MISMATCH`

### POST `/api/auth/login`

요청 JSON:

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

성공 응답 예시:

```json
{
  "success": true,
  "data": {
    "accessToken": "문서에는 실제 토큰을 기록하지 않음",
    "role": "USER",
    "email": "user@example.com",
    "nickname": "발표자"
  },
  "message": "로그인되었습니다.",
  "errorCode": null
}
```

오류 상태 코드: 400 `BAD_CREDENTIALS`, `VALIDATION_ERROR`

## 영상 API

| Method | URL | 인증 | Controller | Service | 소유권 검증 |
|---|---|---|---|---|---|
| POST | `/api/videos` | 필요 | `VideoController.upload` | `VideoFileCommandService.upload` | 업로드 사용자를 영상 소유자로 저장 |
| GET | `/api/videos/{videoId}` | 필요 | `VideoController.detail` | `VideoFileQueryService.detail` | 본인 또는 `ADMIN` |
| GET | `/api/videos/{videoId}/stream` | 필요 | `VideoController.stream` | `VideoFileQueryService.loadOwnedVideo`, `streamResource` | 본인 또는 `ADMIN` |

### POST `/api/videos`

요청 파라미터: `multipart/form-data`의 `file`

성공 응답 예시:

```json
{
  "success": true,
  "data": {
    "videoId": 10,
    "originalFilename": "presentation.mp4",
    "mimeType": "video/mp4",
    "fileSize": 10485760,
    "durationSeconds": 180.25,
    "createdAt": "2026-07-07T19:00:00+09:00"
  },
  "message": "영상이 업로드되었습니다.",
  "errorCode": null
}
```

오류 상태 코드: 400 `INVALID_VIDEO_TYPE`, `VIDEO_FILE_TOO_LARGE`, `VIDEO_TOO_SHORT`, `VIDEO_TOO_LONG`, `INVALID_VIDEO_METADATA`, `STORAGE_ERROR`; 401 인증 실패

업로드 제한은 `backend/src/main/java/.../VideoFileCommandService.java`의 `MAX_VIDEO_BYTES`, `ALLOWED_MIME_TYPES`와 `frontend/src/constants/fileConstants.js`에 있다.

영상 길이는 ffprobe가 읽은 실제 metadata를 기준으로 검증한다. 기본 허용 범위는 5초 이상 600초 이하이며 `VIDEO_MIN_DURATION_SECONDS`, `VIDEO_MAX_DURATION_SECONDS`로 조정한다. 짧은 영상은 `VIDEO_TOO_SHORT`, 긴 영상은 `VIDEO_TOO_LONG`, 판독할 수 없는 영상은 `INVALID_VIDEO_METADATA`를 반환한다. metadata 검증에 실패한 파일은 DB row를 만들지 않고 저장 파일을 정리한다.

## 분석 API

| Method | URL | 인증 | Controller | Service | 소유권 검증 |
|---|---|---|---|---|---|
| POST | `/api/analyses` | 필요 | `AnalysisController.start` | `AnalysisCommandService.start` | `videoId`가 본인 영상인지 확인 |
| GET | `/api/analyses` | 필요 | `AnalysisController.list` | `AnalysisQueryService.list` | 현재 사용자 목록만 |
| GET | `/api/analyses/reports` | 필요 | `AnalysisController.reports` | `AnalysisQueryService.reports` | `ADMIN`은 전체, 그 외 본인만 |
| GET | `/api/analyses/{jobId}/status` | 필요 | `AnalysisController.status` | `AnalysisQueryService.status` | 본인 또는 `ADMIN` |
| POST | `/api/analyses/{jobId}/retry` | 필요 | `AnalysisController.retry` | `AnalysisCommandService.retry` | 본인만 |
| POST | `/api/analyses/{jobId}/cancel` | 필요 | `AnalysisController.cancel` | `AnalysisCommandService.cancel` | 본인만 |
| DELETE | `/api/analyses/{jobId}` | 필요 | `AnalysisController.delete` | `AnalysisCommandService.deleteReport` | 본인 또는 `ADMIN` |
| GET | `/api/analyses/{jobId}/result` | 필요 | `AnalysisController.result` | `ResultQueryService.detailByJobId` | 본인 또는 `ADMIN` |

### POST `/api/analyses`

요청 JSON:

```json
{ "videoId": 10 }
```

성공 응답:

```json
{
  "success": true,
  "data": { "jobId": 20, "status": "QUEUED" },
  "message": "분석 작업이 생성되었습니다.",
  "errorCode": null
}
```

오류 상태 코드: 400 `VALIDATION_ERROR`; 403 `VIDEO_OWNER_MISMATCH`; 404 `VIDEO_NOT_FOUND`; 409 `ANALYSIS_ALREADY_ACTIVE`

`POST /api/analyses`는 같은 `videoId`에 `QUEUED`, `PROCESSING`, `CANCEL_REQUESTED` job이 이미 있으면 새 job을 만들지 않고 409 `ANALYSIS_ALREADY_ACTIVE`를 반환한다. 응답의 `data.jobId`에는 기존 활성 job ID가 포함된다. 종료된 job만 있는 기존 영상에 최초 분석 API를 다시 호출하면 별도 분석 이력으로 새 job을 생성한다. `FAILED`/`CANCELED`의 동일 이력 재실행은 retry API를 사용하며 `DONE`은 retry 대상이 아니다.

```json
{
  "success": false,
  "data": { "jobId": 20 },
  "message": "이미 분석이 진행 중인 영상입니다.",
  "errorCode": "ANALYSIS_ALREADY_ACTIVE"
}
```

### GET `/api/analyses/reports`

요청 파라미터: `page`, `size`, `status`, `keyword`, `sort`. 기본값은 `page=0`, `size=10`, `sort=createdAt,desc`이다. `size` 최대값은 `AnalysisQueryService.MAX_REPORT_PAGE_SIZE=100`이다.

성공 응답 예시:

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "jobId": 20,
        "resultId": 30,
        "title": "presentation.mp4",
        "originalFileName": "presentation.mp4",
        "status": "DONE",
        "retryCount": 0,
        "totalScore": 82.5,
        "createdAt": "2026-07-07T19:00:00+09:00",
        "completedAt": "2026-07-07T19:02:00+09:00",
        "failedAt": null,
        "failureReason": null
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  },
  "message": "리포트 목록입니다.",
  "errorCode": null
}
```

오류 상태 코드: 401 인증 실패, 403 접근 거부

### POST `/api/analyses/{jobId}/retry`

요청 JSON: 없음

성공 응답:

```json
{
  "success": true,
  "data": {
    "jobId": 20,
    "status": "QUEUED",
    "message": "분석을 다시 시작했습니다."
  },
  "message": "분석을 다시 시작했습니다.",
  "errorCode": null
}
```

오류 상태 코드: 403 `ACCESS_DENIED`; 404 `ANALYSIS_JOB_NOT_FOUND`; 409 `ANALYSIS_ALREADY_RUNNING`, `ANALYSIS_RETRY_NOT_ALLOWED`, `ANALYSIS_SOURCE_VIDEO_MISSING`

허용 상태는 `FAILED`, `CANCELED`다. 동일한 `jobId`를 유지하고 기존 결과와 부분 산출물을 정리한 뒤 `QUEUED`로 전환한다.

### POST `/api/analyses/{jobId}/cancel`

요청 JSON: 없음

```json
{
  "success": true,
  "data": {
    "jobId": 20,
    "status": "CANCEL_REQUESTED",
    "message": "분석 중단을 요청했습니다."
  },
  "message": "분석 취소 요청이 처리되었습니다.",
  "errorCode": null
}
```

`QUEUED`는 `CANCELED`, `PROCESSING`은 `CANCEL_REQUESTED`가 된다. `CANCEL_REQUESTED`와 `CANCELED` 재요청은 멱등 성공이며 `DONE`, `FAILED`는 409다.

오류 상태 코드: 403 `ACCESS_DENIED`; 404 `ANALYSIS_JOB_NOT_FOUND`; 409 `ANALYSIS_CANCEL_NOT_ALLOWED`

### DELETE `/api/analyses/{jobId}`

요청 JSON: 없음. `DONE`, `FAILED`, `CANCELED`만 삭제할 수 있다.

오류 상태 코드: 403 `ACCESS_DENIED`; 404 `ANALYSIS_JOB_NOT_FOUND`; 409 `ANALYSIS_DELETE_NOT_ALLOWED`

## 결과 API

| Method | URL | 인증 | Controller | Service | 소유권 검증 |
|---|---|---|---|---|---|
| GET | `/api/results` | 필요 | `ResultController.list` | `ResultQueryService.list` | 현재 사용자 결과만 |
| GET | `/api/results/{resultId}` | 필요 | `ResultController.detail` | `ResultQueryService.detailByResultId` | 본인 또는 `ADMIN` |
| DELETE | `/api/results/{resultId}` | 필요 | `ResultController.delete` | `ResultCommandService.delete` | `AnalysisCommandService.deleteReport`에서 본인 또는 `ADMIN` |

성공 응답의 `scores`, `feedback`, `summary`는 `final_analysis_results`의 JSON 문자열을 파싱한 결과이다.

## 관리자 API

관리자 API는 `/api/admin/**`이며 Spring Security에서 `ADMIN`, `MANAGER` 권한만 접근 가능하다. 단, 사용자 추방과 권한 변경은 `AdminController` 내부에서 `ADMIN`만 허용한다.

| Method | URL | 인증 | Controller | Service | 소유권 검증 |
|---|---|---|---|---|---|
| GET | `/api/admin/users` | `ADMIN`/`MANAGER` | `AdminController.users` | Repository 직접 사용 | 운영 권한 |
| PATCH | `/api/admin/users/{userId}/role` | `ADMIN`/`MANAGER` | `AdminController.updateUserRole` | Repository 직접 사용 | `ADMIN`만 허용 |
| DELETE | `/api/admin/users/{userId}` | `ADMIN`/`MANAGER` | `AdminController.expelUser` | Repository 직접 사용 | `ADMIN`만 허용 |
| GET | `/api/admin/analyses` | `ADMIN`/`MANAGER` | `AdminController.analyses` | Repository 직접 사용 | 운영 권한 |
| DELETE | `/api/admin/analyses/{jobId}` | `ADMIN`/`MANAGER` | `AdminController.deleteAnalysis` | `AnalysisCommandService.deleteReport` | `ADMIN`만 소유권 생략 |
| GET | `/api/admin/health` | `ADMIN`/`MANAGER` | `AdminController.health` | 없음 | 운영 권한 |

`/api/admin/health`는 현재 `analysisEngine`과 `videoLlmEngine`을 실제 healthcheck로 호출하지 않고 `"MOCK"` 문자열을 반환한다.

관리자 분석 삭제도 공통 삭제 Service와 `DONE`, `FAILED`, `CANCELED` 상태 제한을 사용한다. `MANAGER`는 endpoint 접근은 가능하지만 공통 Service에서 임의 사용자 job의 소유권 검사를 생략하지 않는다.

## 날짜 형식

날짜 응답 DTO는 `OffsetDateTime`을 사용하며 서비스 시간대 offset을 포함한다. 예: `"2026-07-07T19:00:00+09:00"`. 프론트는 `Asia/Seoul`로 표시하며 timezone 정보 없는 문자열을 임의로 UTC로 추측하지 않는다.
