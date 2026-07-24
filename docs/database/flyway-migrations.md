# Flyway 마이그레이션 운영

## 적용 기준

- Spring Boot: `3.3.5`
- Flyway: `11.20.2`
- MySQL: `8.4`
- migration 위치: `backend/src/main/resources/db/migration`
- Hibernate: `spring.jpa.hibernate.ddl-auto=validate`
- Flyway clean: 비활성화

애플리케이션 스키마의 생성과 변경은 Flyway만 담당한다. Hibernate는 시작 시 Entity와 실제 스키마가 맞는지만 검증하며 DB를 자동 변경하지 않는다.

## 현재 migration

| 버전 | 파일 | 역할 |
|---|---|---|
| V1 | `V1__baseline_schema.sql` | 완전히 빈 DB에 현재 5개 애플리케이션 테이블과 제약조건 생성 |
| V2 | `V2__add_query_indexes.sql` | Repository 조회 패턴에 필요한 복합 인덱스 4개 추가 |

V1은 `users`, `refresh_tokens`, `uploaded_videos`, `analysis_jobs`, `final_analysis_results`를 생성한다. 기존 DB에만 존재하는 수동 백업 테이블 `final_analysis_results_backup_20260706`은 애플리케이션 Entity가 아니므로 migration 관리 대상에 포함하지 않았다.

## 기존 DB 최초 도입

현재 운영 볼륨은 이미 baseline V1과 V2 적용이 완료됐다. 다른 기존 DB에 최초 도입할 때만 다음 순서를 사용한다.

1. DB와 storage를 먼저 통합 백업한다.
2. 해당 1회 실행에만 `FLYWAY_BASELINE_ON_MIGRATE=true`, `FLYWAY_BASELINE_VERSION=1`을 설정한다.
3. backend를 시작해 기존 스키마를 V1로 표시하고 V2 이상을 적용한다.
4. `flyway_schema_history`의 성공 여부와 기존 테이블별 row 수를 확인한다.
5. 즉시 `FLYWAY_BASELINE_ON_MIGRATE=false`로 되돌리고 backend를 다시 시작한다.

`baseline-on-migrate=true`를 상시 운영값으로 두면 잘못 지정한 비어 있지 않은 DB를 정상 DB로 오인할 수 있다. 기본값과 실제 Docker 환경은 반드시 `false`를 유지한다.

## 신규 빈 DB

빈 DB에서는 baseline을 사용하지 않는다. backend 시작 시 V1부터 순서대로 실행되어 전체 스키마가 생성된다.

```powershell
docker compose `
  --env-file .\infra\docker\.env `
  -f .\infra\docker\docker-compose.yml `
  up -d --build backend
```

## 이력 확인

MySQL client에 비밀번호를 프롬프트로 입력한 뒤 다음 SQL을 실행한다.

```powershell
docker compose `
  --env-file .\infra\docker\.env `
  -f .\infra\docker\docker-compose.yml `
  exec mysql mysql -u presentation -p presentation_coaching_ai
```

```sql
SELECT installed_rank, version, description, type, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

## 새 migration 작성 규칙

- 이미 적용된 V1/V2 파일은 수정하지 않는다.
- 변경은 다음 순번의 `V<번호>__<설명>.sql` 파일로 추가한다.
- migration 전 DB 백업을 생성한다.
- `DROP`, 대량 `UPDATE`, 컬럼 축소 같은 파괴적 변경은 복제 DB에서 먼저 검증한다.
- Entity 변경과 migration SQL을 같은 작업에서 맞춘다.
- MySQL 8.4 빈 DB, 기존 schema, JPA validate를 모두 검사한다.
- 실제 비밀번호, 사용자 데이터, 관리자 계정 생성문을 migration에 넣지 않는다.

## checksum 오류

적용된 migration 파일이 나중에 수정되면 Flyway validation이 checksum mismatch로 backend 시작을 막는다.

1. 어떤 파일이 언제 변경됐는지 Git diff와 배포 이력을 먼저 확인한다.
2. 운영 DB가 실제로 어떤 SQL을 적용받았는지 확인한다.
3. 기존 migration을 원상 복구하고 필요한 변경은 새 버전으로 작성한다.
4. 원인을 확인하지 않은 `flyway repair` 사용은 금지한다.

`repair`는 잘못된 스키마를 고치지 않고 이력만 바꿀 수 있으므로 복구 책임자의 명시적 판단과 백업 없이는 실행하지 않는다.

## 검증 결과

2026-07-24 기준으로 다음을 확인했다.

- 기존 DB: baseline V1, V2 적용 후 기존 row 수 보존
- 빈 MySQL 8.4: V1/V2 실행, 5개 애플리케이션 테이블 생성
- 복원 DB: Flyway V2 인식 및 backend JPA validate 성공
- Flyway 11.20.2: MySQL 8.4에서 버전 경고 없이 시작
