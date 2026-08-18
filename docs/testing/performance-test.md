# 성능 검증 결과

## 목적

현재 Docker Compose 구성에서 API 응답 시간, 1~3분 영상의 분석 시간,
동시 5건 요청 안정성을 측정한다. 이 검증은 점수 계산식, MediaPipe 판정
기준, NVIDIA 모델, DB 스키마를 변경하지 않는다.

측정일은 2026-08-18이며 결과 원본은 Git에서 제외되는
`artifacts/performance/`에 저장한다. 샘플 영상은 가상의 발표자 이미지와
합성 음성으로 생성했으며 실제 사용자 정보는 포함하지 않는다.

## 테스트 환경

| 항목 | 값 |
|---|---|
| CPU | AMD Ryzen 9 5950X, 16코어 32스레드 |
| 호스트 메모리 | 31.92 GiB |
| Docker 할당 | 32 CPU, 15.57 GiB |
| Docker Engine | 29.6.1 |
| 실행 구성 | Nginx, Spring Boot, Analysis Engine, Video LLM Engine, MySQL |
| 샘플 길이 | 60, 90, 120, 150, 180초 |
| 동시 요청 | 배치당 5건 |
| 반복 횟수 | 4배치, 총 20건 |

고정 이미지에 미세한 확대 효과와 반복 합성 음성을 사용했다. 따라서 영상
길이가 늘어도 시각 샘플의 다양성이 커지지 않으며, 길이와 처리시간이 항상
비례하지 않는 한계가 있다.

## 실행 설정

### Backend executor

`backend/src/main/java/com/hanium/presentation/config/AsyncConfig.java` 기준이다.

| 설정 | 값 |
|---|---:|
| executor | `ThreadPoolTaskExecutor` |
| corePoolSize | 2 |
| maxPoolSize | 4 |
| queueCapacity | 50 |
| threadNamePrefix | `analysis-` |
| shutdown 대기 | 30초 |
| rejection policy | 명시 없음, 기본 `AbortPolicy` |

`@Async("analysisTaskExecutor")`는 일반 분석과 복구 분석에 사용된다. 큐
용량이 50이므로 동시 5건에서는 core thread 2개가 실행되고 나머지는 먼저
큐에 들어간다. 이 부하에서는 maxPoolSize 4까지 확장되지 않는다.

### Engine worker와 DB pool

- Analysis Engine: Uvicorn 단일 worker, 동기 route는 Starlette thread pool에서 실행
- Video LLM Engine: Uvicorn 단일 worker, 별도 동시 호출 제한 없음
- HikariCP: 프로젝트에 명시적 pool 설정 없음
- HikariCP 기본값: maximumPoolSize 10, minimumIdle 10, connectionTimeout 30초,
  idleTimeout 600초
- 측정 중 MySQL `Max_used_connections=11`; pool 고갈 로그 없음

### Timeout

| 구간 | 현재 값 |
|---|---:|
| Nginx `proxy_read_timeout` / `proxy_send_timeout` | 300초 |
| Backend -> Analysis Engine | 600초 |
| Backend -> Video LLM Engine | 600초 |
| NVIDIA 호출 | 180초 |
| Ollama Video LLM 호출 | 240초 |
| Text LLM 호출 | 30초 |

20건 mock 부하에서는 timeout 실패가 없었다. 실제 Provider 1건에서는
NVIDIA 호출이 성공했지만 Text LLM의 Ollama 호출이 30초에 timeout되어
결정적 fallback이 사용되었다. 표본이 1건이므로 이번 작업에서는 timeout을
변경하지 않는다.

## API 응답 시간

Nginx 경유로 각 조회 API를 warm-up 후 50회 측정했다. 업로드와 분석 시작은
20회, 동일 job 재시도는 동시성 시나리오에서 1회 측정했다.

| API | 횟수 | 평균 | median | p95 | p99 | 최대 | 오류율 |
|---|---:|---:|---:|---:|---:|---:|---:|
| 로그인 | 50 | 73.95ms | 77.37ms | 81.95ms | 85.37ms | 86.14ms | 0% |
| 리포트 목록 | 50 | 26.08ms | 30.37ms | 33.27ms | 39.29ms | 42.08ms | 0% |
| 분석 상태 | 50 | 18.07ms | 15.95ms | 31.07ms | 31.53ms | 31.53ms | 0% |
| job 결과 조회 | 50 | 19.74ms | 22.85ms | 31.54ms | 31.64ms | 31.65ms | 0% |
| 결과 상세 | 50 | 23.14ms | 30.06ms | 32.16ms | 32.34ms | 32.47ms | 0% |
| 업로드 | 20 | 120.87ms | 115.06ms | 164.17ms | 164.17ms | 180.25ms | 0% |
| 분석 시작 | 20 | 31.42ms | 28.38ms | 53.98ms | 53.98ms | 56.46ms | 0% |
| 재시도 | 1 | 28.47ms | 28.47ms | 28.47ms | 28.47ms | 28.47ms | 0% |

업로드 처리량은 평균 13.75 MiB/s, median 13.61 MiB/s, p95 21.02 MiB/s였다.
분석 시작 API 응답과 실제 분석 완료 시간은 별도로 측정했다.

Backend 직통과 Nginx 경유 p95 차이는 -7.41~ -0.08ms 범위였다. 실행 순서와
캐시 오차 범위에서 Nginx 오버헤드는 관찰되지 않았으며 현재 병목이 아니다.

## 길이별 분석 결과

각 길이는 4회 측정했다. 아래 분석 시간은 `PROCESSING`부터 종료 상태까지,
Engine 시간과 RTF는 Analysis Engine 내부 계측값이다.

| 길이 | 평균 queue | 평균 분석 | 평균 전체 | Text | Audio | Vision | Gesture | Engine 전체 | 평균 RTF |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 60초 | 14.136초 | 41.828초 | 55.473초 | 13.805초 | 0.259초 | 11.127초 | 14.850초 | 40.127초 | 0.669 |
| 120초 | 38.243초 | 69.452초 | 107.582초 | 23.691초 | 0.456초 | 22.418초 | 21.120초 | 67.784초 | 0.565 |
| 180초 | 95.121초 | 59.769초 | 154.890초 | 26.810초 | 0.589초 | 16.212초 | 14.476초 | 58.198초 | 0.324 |

모든 길이의 평균 RTF가 1 미만이었다. 180초 결과가 120초보다 짧은 것은
반복 영상과 프레임 샘플 상한의 영향이므로 일반 영상에 그대로 외삽하면 안 된다.

20건 전체 분포는 다음과 같다.

| 지표 | 평균 | median | p95 | p99 | 최대 |
|---|---:|---:|---:|---:|---:|
| queue wait | 43.575초 | 41.009초 | 122.048초 | 131.987초 | 131.987초 |
| analysis | 58.791초 | 57.316초 | 78.606초 | 91.330초 | 91.330초 |
| turnaround | 102.125초 | 110.381초 | 164.277초 | 183.993초 | 183.993초 |

DB 시간 컬럼의 초 단위 정밀도 때문에 즉시 실행된 일부 job의 queue wait가
0초로 보이고 전체 시간에 1초 미만 오차가 있다.

## 동시 5건과 성공률

- 요청: 5건 동시 시작 x 4배치 = 20건
- DONE: 20건
- FAILED: 0건
- CANCELED: 0건
- permanent QUEUED/PROCESSING: 0건
- 성공률: 100%
- 중복 job, DB deadlock, executor rejection, container crash, OOM: 0건
- QUEUED job 재시도 보호: 같은 jobId 유지, API 28.47ms

Mock 부하에서는 Provider 비용을 피하기 위해 Video LLM과 Text LLM을 `NONE`
으로 실행했다. 이때 deterministic fallback은 각각 20/20이며 의도된 값이다.
Vision fallback과 Gesture fallback은 각각 0/20이다.

실제 Provider 연결 검증 1건은 60초 샘플로 수행했다.

- 상태: DONE
- Video LLM: `NVIDIA_NIM`, `nvidia/nemotron-nano-12b-v2-vl`
- Video LLM fallback: 미사용
- Analysis Engine: 23.197초
- Video LLM: 37.143초
- Text LLM: 30.010초 후 fallback
- 전체 pipeline: 90.650초

실제 Provider 20회 호출은 비용과 외부 서비스 변동을 피하기 위해 수행하지 않았다.

## 자원 사용량

동시 5건 부하 중 161회 `docker stats`를 수집했다.

| 서비스 | 평균 CPU | 최대 CPU | 평균 메모리 | 최대 메모리 |
|---|---:|---:|---:|---:|
| Backend | 5.45% | 66.48% | 550.92 MiB | 621.00 MiB |
| Analysis Engine | 783.92% | 1667.92% | 1882.77 MiB | 2710.53 MiB |
| Video LLM Engine | 16.56% | 653.35% | 46.42 MiB | 159.60 MiB |
| MySQL | 2.01% | 8.15% | 465.36 MiB | 466.50 MiB |

Analysis Engine은 평균 약 7.8개 CPU 코어, 최대 약 16.7개 코어를 사용했다.
메모리는 최대 약 2.65 GiB였고 OOM 또는 배치별 지속 증가 징후는 없었다.
장시간 메모리 누수 여부는 이번 약 10분 부하만으로 확정할 수 없다.

## 병목과 운영 권장값

주 병목은 CPU-bound인 Whisper/MediaPipe를 수행하는 Analysis Engine과 backend
executor 대기열이다. Backend, MySQL, Nginx는 병목 징후가 없었다.

현재 PC에서는 **동시 분석 2건 + 나머지 QUEUED** 정책을 권장한다. 동시 5건을
모두 실행하면 Analysis Engine의 CPU 경합이 커질 가능성이 높다. 현재 executor는
동시 5건에서 실제로 이 정책처럼 동작하며 성공률 100%를 달성했다.

따라서 executor, DB pool, timeout은 변경하지 않았다. queueCapacity가 충분해서
maxPoolSize가 사실상 사용되지 않는 점은 명시적으로 유지할 정책인지 후속 검토가
필요하다. 운영에서 queue 길이와 queue p95를 모니터링한 뒤, 처리량이 부족할 때만
프로세스 격리 또는 별도 작업 큐를 검토한다.

## 실행 방법

API 지연 측정:

```powershell
$env:PERF_EMAIL = "테스트 계정 이메일"
$env:PERF_PASSWORD = "테스트 계정 비밀번호"
python .\scripts\performance\api_latency_test.py `
  --base-url http://localhost `
  --job-id 1 `
  --result-id 1 `
  --iterations 50 `
  --output .\artifacts\performance\api-latency.json
```

동시 분석 측정:

```powershell
python .\scripts\performance\analysis_load_test.py `
  --base-url http://localhost `
  --samples .\sample-60.mp4 .\sample-90.mp4 .\sample-120.mp4 .\sample-150.mp4 .\sample-180.mp4 `
  --batches 4 `
  --concurrency 5 `
  --exercise-retry `
  --output .\artifacts\performance\load.json
```

스크립트 결과에는 인증 토큰, 이메일, 사용자 ID, 영상 경로와 파일명을 저장하지
않는다. 실제 Provider 검증 시 API 키와 Authorization header도 출력하지 않는다.

## 판정

현재 PC 기준 합격이다. 일반 조회 API p95는 500ms 미만, 분석 시작 응답은 1초
미만, 20건 성공률은 100%이며 영구 대기·OOM·container crash·DB deadlock이
없었다. 남은 성능 위험은 실제 다양한 영상에서의 처리시간 분포와 Text LLM
30초 timeout이다. 이 두 항목은 더 큰 비식별 표본으로 별도 검증해야 한다.
