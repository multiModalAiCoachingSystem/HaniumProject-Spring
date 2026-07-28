# DB 및 Storage 백업·복원

## 기본 정책

백업은 프로젝트 외부 경로에 저장한다. 예시 환경변수는 다음과 같다.

```dotenv
BACKUP_ROOT=D:/presentation-coaching-backups
BACKUP_RETENTION_DAYS=14
BACKUP_KEEP_MINIMUM=3
BACKUP_INCLUDE_TEMP_ARTIFACTS=false
```

실제 경로는 `infra/docker/.env`에만 기록하거나 실행 시 `-BackupRoot`로 전달한다. dump, archive, checksum, 실제 `.env`는 Git에 커밋하지 않는다.

## 백업 범위

MySQL 백업에는 schema, data, trigger, routine, event가 포함된다.

Storage 기본 백업 대상:

- `uploads`: 원본 업로드 영상
- `streams`: 변환 영상과 스트리밍 산출물
- `analysis`, `thumbnails`, `results`: 존재하는 경우 포함

기본 제외 대상:

- `models`: 재다운로드 가능한 모델
- `regression-samples`: 테스트 자산
- `video-llm`, `tmp`: 임시 분석 산출물
- cache와 log

`BACKUP_INCLUDE_TEMP_ARTIFACTS=true` 또는 `-IncludeTempArtifacts:$true`일 때만 `video-llm`, `tmp`를 추가한다.

## 개별 백업

```powershell
.\scripts\backup\backup-mysql.ps1

.\scripts\backup\backup-storage.ps1
```

`.env`에 `BACKUP_ROOT`가 없으면 안전하게 중단한다. 다음처럼 경로를 명시할 수도 있다.

```powershell
.\scripts\backup\backup-mysql.ps1 `
  -BackupRoot '<프로젝트-외부-백업-경로>'
```

## 통합 백업

```powershell
.\scripts\backup\backup-all.ps1
```

통합 백업은 다음 순서로 실행된다.

1. `PROCESSING`, `CANCEL_REQUESTED` 작업 수 확인
2. MySQL 일관성 dump 생성
3. storage archive 생성
4. `manifest.json` 생성
5. DB, storage, manifest의 SHA-256을 `checksums.sha256`에 기록
6. 모든 단계가 성공한 경우에만 `.partial` 디렉터리를 최종 백업 세트로 변경
7. retention 정책 적용

활성 분석이 있으면 기본적으로 중단한다. `-AllowActiveJobs`는 DB와 파일 시점이 달라질 수 있음을 이해한 운영자만 사용한다.

manifest에는 파일명, 크기, 생성 시각, Git commit, Flyway/schema 버전, 시작 시 활성 작업 수만 기록한다. 비밀번호, API Key, 영상 경로, 사용자 정보는 기록하지 않는다.

## MySQL 복원

복원은 대상 DB를 자동으로 덮어쓰지 않는다. 대상 DB명과 일치하는 확인 문자열이 필요하다.

```powershell
.\scripts\backup\restore-mysql.ps1 `
  -DumpFile '<백업-경로>\database\presentation_coaching_ai_YYYYMMDD_HHMMSS.sql'
```

프롬프트에 다음 형식으로 입력한다.

```text
RESTORE presentation_coaching_ai
```

기본값은 복원 직전 대상 DB를 다시 백업한다. 별도 테스트 컨테이너 복원 시 `-TargetContainer`, `-TargetDatabase`를 지정할 수 있다. 대상 컨테이너의 `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_DATABASE` 환경이 구성되어 있어야 한다.

복원 후 반드시 핵심 테이블, row 수, `flyway_schema_history`, backend Flyway/JPA validate, 로그인과 리포트 조회를 확인한다.

## Storage 복원

기존 파일을 덮어쓰지 않도록 대상 경로는 없거나 비어 있어야 한다.

```powershell
.\scripts\backup\restore-storage.ps1 `
  -ArchiveFile '<백업-경로>\sets\YYYYMMDD_HHMMSS\storage_YYYYMMDD_HHMMSS.zip' `
  -ChecksumFile '<백업-경로>\sets\YYYYMMDD_HHMMSS\checksums.sha256' `
  -TargetDirectory '<복원-검증-storage-경로>'
```

스크립트는 checksum, archive 열기, 절대경로와 `..` 경로를 검사한다. 복원 후 원본과 파일 수 및 총 바이트를 비교한 뒤 서비스 mount를 전환한다.

## 전체 복구 순서

1. 장애 난 컨테이너의 쓰기를 중단하고 현재 상태를 별도 보존한다.
2. 사용할 통합 백업 세트의 `manifest.json`과 `checksums.sha256`을 확인한다.
3. storage를 새 빈 디렉터리에 복원하고 파일 수·크기를 검증한다.
4. 별도 MySQL에 dump를 복원하고 row 수·Flyway 이력을 검증한다.
5. backend를 복원 DB에 연결해 Flyway/JPA validate를 확인한다.
6. 검증된 DB와 storage로 서비스 연결을 전환한다.
7. 로그인, 리포트 목록, 결과 상세, 파일 재생을 확인한다.

운영 DB에 바로 복원하거나 `docker compose down -v`를 실행하지 않는다.

## Retention

- 기본 보관 기간: 14일
- 최소 보관 세트: 3개
- 오늘 생성한 백업: 당일 삭제 금지
- 백업 루트 밖의 파일: 삭제 금지

Retention은 성공한 최종 파일 또는 세트만 대상으로 한다. `.partial` 실패 산출물은 해당 실행에서 정리한다.

## Windows 작업 스케줄러

이번 구현은 작업을 자동 등록하지 않는다. 매일 새벽 2시 등록 예시는 다음과 같다.

```powershell
$projectRoot = '<프로젝트-루트>'
$action = New-ScheduledTaskAction `
  -Execute 'powershell.exe' `
  -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$projectRoot\scripts\backup\backup-all.ps1`""
$trigger = New-ScheduledTaskTrigger -Daily -At 2am
Register-ScheduledTask `
  -TaskName 'PresentationCoachingBackup' `
  -Action $action `
  -Trigger $trigger `
  -Description 'MySQL and storage integrated backup'
```

등록 전 서비스 계정의 Docker 접근 권한, D 드라이브 쓰기 권한, 실패 exit code 수집 위치를 결정한다.

## 실제 복원 훈련

2026-07-24 통합 백업과 별도 복원 훈련 결과:

- schema version: V2
- DB dump 복원: 성공
- 기존 핵심 row 수: 모두 일치
- 복원 DB backend 시작 및 JPA validate: 성공
- storage archive: 약 15.9GB
- storage 파일: 원본 63개, 복원 63개
- storage 총 바이트: 원본과 복원 일치
- 검증용 임시 DB, 컨테이너, storage 복사본: 검증 후 제거
