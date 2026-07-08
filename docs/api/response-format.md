# 응답 형식

## 백엔드 공통 응답

Spring Boot API는 `backend/src/main/java/com/hanium/presentation/global/response/ApiResponse.java`의 `ApiResponse<T>`로 응답을 감싼다.

```json
{
  "success": true,
  "data": {},
  "message": "처리 메시지",
  "errorCode": null
}
```

실패 응답:

```json
{
  "success": false,
  "data": null,
  "message": "오류 메시지",
  "errorCode": "ERROR_CODE"
}
```

## 페이지 응답

`GET /api/analyses/reports`는 `PageResponse<T>`를 `data`에 담는다.

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

## 오류 코드와 상태 코드

상태 코드는 `GlobalExceptionHandler.statusOf()`에서 매핑한다.

| HTTP | errorCode |
|---:|---|
| 400 | 대부분의 `BusinessException`, `VALIDATION_ERROR` |
| 403 | `ACCESS_DENIED`, `VIDEO_OWNER_MISMATCH` |
| 404 | `USER_NOT_FOUND`, `VIDEO_NOT_FOUND`, `ANALYSIS_JOB_NOT_FOUND`, `RESULT_NOT_FOUND` |
| 409 | `ANALYSIS_ALREADY_RUNNING`, `ANALYSIS_RETRY_NOT_ALLOWED`, `ANALYSIS_SOURCE_VIDEO_MISSING`, `ANALYSIS_DELETE_NOT_ALLOWED` |

## FastAPI 엔진 응답

`analysis-engine`과 `video-llm-engine`은 백엔드처럼 `success/data/message/errorCode`로 감싸지 않는다. 각 엔진은 분석 결과 dict를 직접 반환한다. 백엔드가 이 응답을 DTO로 받아 `final_analysis_results`에 병합 저장한다.

## 보안 주의

문서 예시의 토큰, 파일 경로, 사용자 이메일은 예시 값이다. 실제 `JWT_SECRET`, refresh token, DB 비밀번호, API 키는 문서에 기록하지 않는다.
