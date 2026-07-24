# 데이터베이스 복구 문제 해결

## backend가 Flyway 단계에서 시작되지 않음

로그에서 MySQL health, DB 연결 환경변수, 기존 볼륨의 실제 계정 비밀번호, `flyway_schema_history`, checksum mismatch와 JPA validation 오류를 순서대로 확인한다.

```powershell
docker compose `
  --env-file .\infra\docker\.env `
  -f .\infra\docker\docker-compose.yml `
  ps

docker compose `
  --env-file .\infra\docker\.env `
  -f .\infra\docker\docker-compose.yml `
  logs --tail 200 backend mysql
```

로그를 공유할 때 비밀번호, JWT, API Key, 사용자 데이터는 제거한다.

## 기존 볼륨에서 DB 인증 실패

MySQL 공식 이미지는 볼륨 최초 생성 시에만 `MYSQL_PASSWORD`를 적용한다. 이후 `.env` 값만 바꿔도 기존 계정 비밀번호는 바뀌지 않는다.

- `DB_PASSWORD`는 MySQL 애플리케이션 계정의 실제 비밀번호와 같아야 한다.
- 원인을 해결하기 위해 volume을 삭제하지 않는다.
- 계정 비밀번호 변경은 DB 관리자 절차와 사전 백업 후 수행한다.

## checksum mismatch

- Git diff와 배포된 migration 원본을 비교한다.
- DB 스키마를 임의 변경하거나 `repair`부터 실행하지 않는다.
- 적용된 파일을 원상 복구하고 새 변경은 다음 V번호 migration으로 작성한다.
- 복구 전 통합 백업을 생성한다.

## migration SQL 실패

1. 실패한 버전과 SQL State를 확인한다.
2. 같은 MySQL 8.4의 별도 복원 DB에서 재현한다.
3. DDL이 일부 적용됐는지 `SHOW CREATE TABLE`, `SHOW INDEX`로 확인한다.
4. 자동 `DROP`, volume 삭제, 데이터 초기화를 하지 않는다.
5. rollback SQL 또는 후속 migration은 데이터 영향을 검토한 뒤 작성한다.

MySQL DDL은 문장별 자동 commit이 발생할 수 있어 애플리케이션 트랜잭션처럼 전체가 자동 rollback된다고 가정하지 않는다.

## dump 복원 실패

- dump와 checksum 파일이 같은 백업 세트인지 확인한다.
- dump 크기가 0보다 큰지, `CREATE TABLE`이 있는지 확인한다.
- 대상 컨테이너와 대상 DB명을 다시 확인한다.
- 대상 DB에 기존 객체가 있으면 먼저 별도 백업하고 복제 DB에서 검증한다.
- 확인 문자열이 맞지 않으면 복원은 시작되지 않는다.

운영 DB에 검증되지 않은 dump를 직접 넣지 않는다.

## storage 복원 실패

- archive와 `checksums.sha256`의 파일명이 일치하는지 확인한다.
- 대상 디렉터리가 비어 있는지 확인한다.
- D 드라이브 여유 공간을 확인한다.
- path traversal 오류가 발생하면 archive를 사용하지 않는다.
- 원본 storage를 삭제하거나 덮어쓰지 않는다.

## DB와 storage 시점 불일치

통합 백업은 활성 `PROCESSING`, `CANCEL_REQUESTED` 작업이 있으면 기본 중단한다. `-AllowActiveJobs`로 만든 백업은 DB와 파일 생성 시점이 다를 수 있다.

복구 후 `uploaded_videos.relative_path`, DONE 결과 파일, CANCELED/FAILED 부분 산출물, 다른 job의 원본 참조를 점검한다.

## 절대 금지

- `docker compose down -v`
- `mysql-data` volume 삭제
- 원인 확인 전 `flyway repair`
- 검증 없는 운영 DB restore
- 기존 migration 파일 수정
- backup checksum 무시
- 실제 비밀번호나 dump를 Git에 추가

