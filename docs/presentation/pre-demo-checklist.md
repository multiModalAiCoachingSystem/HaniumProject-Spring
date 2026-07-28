# 최종 시연 체크리스트

## 전날

- 개인정보 없는 시연 계정과 영상을 준비한다.
- 기존 `DONE` 리포트 중 자세·시선·제스처·표정 차이가 설명 가능한 샘플을 고른다.
- 결과 카드에 fallback 코드, 영어 raw 문장, 중복 observation이 노출되지 않는지 확인한다.
- NVIDIA 사용 샘플은 metadata의 `actualProvider`, `actualModel`, `fallbackUsed`를 확인한다.
- DB와 storage 통합 백업을 만들고 checksum을 확인한다.

## 시작 30분 전

```powershell
docker compose `
  --env-file .\infra\docker\.env `
  -f .\infra\docker\docker-compose.yml `
  up -d --build --force-recreate

docker compose `
  --env-file .\infra\docker\.env `
  -f .\infra\docker\docker-compose.yml `
  ps
```

- `http://localhost` 접속과 로그인 확인
- MySQL, analysis-engine health 확인
- Ollama 모델 목록과 NVIDIA key SET/MISSING 상태 확인
- storage 여유 공간 확인
- 브라우저 확대율 100%, 알림과 개인정보가 보이는 탭 정리

## 5~7분 시연

1. 피치메이트 목적과 5개 정량 평가 항목 소개
2. 로그인과 촬영 가이드 설명
3. 새 영상 업로드 및 자동 분석 시작
4. `QUEUED`/`PROCESSING` 상태와 취소 가능 흐름 설명
5. 분석 대기 중 미리 완료된 리포트 보관함 열기
6. 종합 점수와 자세·표정·시선·음성·제스처 점수 설명
7. 낮은 감지 신뢰도 경고와 상세 지표 접기/펼치기 설명
8. 강점·개선 필요 및 timestamp 카드 확인
9. 한국어 최종 피드백과 예상 질문 확인
10. 실패·취소 시 동일 job 재분석과 삭제 정책 설명
11. Spring Boot, 두 FastAPI 엔진, 공유 storage, MySQL 구조 요약

## 장애 대비

- 새 분석이 오래 걸리면 기존 `DONE` 리포트로 즉시 전환한다.
- NVIDIA가 실패하면 Ollama 또는 결정적 fallback으로 전체 분석은 계속된다는 점을 설명한다.
- 네트워크가 끊겨도 로컬 정량 분석과 기존 결과 시연을 우선한다.
- 시연 중 DB 볼륨 삭제, migration 수정, 대량 데이터 정리는 하지 않는다.
