# Backend API

공통 base URL은 `/api`이다. 프론트엔드 `frontend/src/api/httpClient.js`는 `baseURL: "/api"`와 `Authorization: Bearer <accessToken>` 헤더를 사용한다. 응답 래퍼는 `ApiResponse<T>`이며 구조는 `docs/api/response-format.md`를 따른다.

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
    "createdAt": "2026-07-07T19:00:00"
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
    "durationSeconds": null,
    "createdAt": "2026-07-07T19:00:00"
  },
  "message": "영상이 업로드되었습니다.",
  "errorCode": null
}
```

오류 상태 코드: 400 `INVALID_VIDEO_TYPE`, `VIDEO_FILE_TOO_LARGE`, `STORAGE_ERROR`; 401 인증 실패

업로드 제한은 `backend/src/main/java/.../VideoFileCommandService.java`의 `MAX_VIDEO_BYTES`, `ALLOWED_MIME_TYPES`와 `frontend/src/constants/fileConstants.js`에 있다.

## 분석 API

| Method | URL | 인증 | Controller | Service | 소유권 검증 |
|---|---|---|---|---|---|
| POST | `/api/analyses` | 필요 | `AnalysisController.start` | `AnalysisCommandService.start` | `videoId`가 본인 영상인지 확인 |
| GET | `/api/analyses` | 필요 | `AnalysisController.list` | `AnalysisQueryService.list` | 현재 사용자 목록만 |
| GET | `/api/analyses/reports` | 필요 | `AnalysisController.reports` | `AnalysisQueryService.reports` | `ADMIN`은 전체, 그 외 본인만 |
| GET | `/api/analyses/{jobId}/status` | 필요 | `AnalysisController.status` | `AnalysisQueryService.status` | 본인 또는 `ADMIN` |
| POST | `/api/analyses/{jobId}/retry` | 필요 | `AnalysisController.retry` | `AnalysisCommandService.retry` | 본인만 |
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

오류 상태 코드: 400 `VALIDATION_ERROR`; 403 `VIDEO_OWNER_MISMATCH`; 404 `VIDEO_NOT_FOUND`

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
        "createdAt": "2026-07-07T19:00:00",
        "completedAt": "2026-07-07T19:02:00",
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
| DELETE | `/api/admin/analyses/{jobId}` | `ADMIN`/`MANAGER` | `AdminController.deleteAnalysis` | Repository 직접 사용 | 운영 권한 |
| GET | `/api/admin/health` | `ADMIN`/`MANAGER` | `AdminController.health` | 없음 | 운영 권한 |

`/api/admin/health`는 현재 `analysisEngine`과 `videoLlmEngine`을 실제 healthcheck로 호출하지 않고 `"MOCK"` 문자열을 반환한다.
