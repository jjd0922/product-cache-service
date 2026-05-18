# Benchmark

상품 조회 캐시 적용 효과를 DB only 모드와 cache enabled 모드로 비교하기 위한 부하 테스트 기록이다.

현재 문서에는 전달받은 k6 콘솔 캡처와 actuator metric 캡처 기준의 **cache enabled 측정값**을 반영했다. DB only 지연시간은 아직 별도 캡처가 없어 Before/After latency 비교값으로 확정하지 않는다.

## 측정 환경

| 항목 | 값 |
|---|---|
| 측정 도구 | k6 v0.34.1 |
| 실행 방식 | local |
| 데이터셋 | `loadtest/sql/seed-products.sql` 기준 10,000건 |
| App | `product-api` local bootRun |
| DB | MySQL 8.0 Docker Compose |
| Cache | Redis 7 Docker Compose |
| Cache mode | cache enabled |
| VUs | 200 observed |
| 측정 시간 | 각 시나리오 6분 |

## 시나리오

| 시나리오 | 스크립트 | 목표 부하 |
|---|---|---|
| 단건 조회 | `loadtest/k6/product-single.js` | 최대 1,000 iters/s, 6분 실행 |
| 다건 조회 | `loadtest/k6/product-batch.js` | 20 ids/request, 최대 500 iters/s, 6분 실행 |
| Hot/Cold 조회 | `loadtest/k6/product-hot-cold.js` | 90% hot key, 10% cold key, 최대 1,000 iters/s, 6분 실행 |

## Cache Enabled 측정 결과

| 시나리오 | Checks | 실패율 | avg | p50 | p90 | p95 | p99 | max | 처리량 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 단건 조회 | 210,148 | 0.00% | 3.61ms | 2.15ms | 9.18ms | 13.04ms | 17.18ms | 123.72ms | 583.74 req/s |
| 다건 조회 | 105,149 | 0.00% | 4.83ms | 2.76ms | 4.75ms | 13.33ms | 29.38ms | 260.20ms | 292.08 req/s |
| Hot/Cold 조회 | 210,149 | 0.00% | 3.23ms | 2.13ms | 4.99ms | 11.60ms | 16.86ms | 100.70ms | 583.73 req/s |

## Cache Metric Delta

Actuator metric은 누적 카운터이므로 테스트 전후 값을 빼서 delta를 계산했다.

| 시나리오 | fallback 시작 | fallback 종료 | fallback delta | DB fallback QPS | read hit delta | read miss delta | Redis hit ratio |
|---|---:|---:|---:|---:|---:|---:|---:|
| 단건 조회 | 23,134 | 33,135 | 10,001 | 27.78/s | 423,394 | 36,906 | 91.98% |
| 다건 조회 | 33,135 | 53,131 | 19,996 | 55.54/s | 4,225,956 | 59,988 | 98.60% |
| Hot/Cold 조회 | 53,131 | 56,033 | 2,902 | 8.06/s | 423,197 | 8,709 | 97.98% |

### 해석

- 단건 조회는 583.74 req/s 부하에서 DB fallback이 27.78/s로 제한됐다. 단건 요청 기준으로 보면 전체 요청량 대비 DB fallback은 약 4.8% 수준이다.
- 다건 조회는 292.08 req/s, 요청당 20개 상품 조회 조건에서 DB fallback이 55.54/s로 제한됐다. 조회 item 기준으로는 약 5,841 item/s 부하 중 약 1.0%만 DB fallback으로 흘렀다.
- Hot/Cold 조회는 hot key 재사용 효과로 DB fallback이 8.06/s까지 낮아졌고, Redis hit ratio도 97.98%로 확인됐다.

## DB Only / Cache Enabled 비교표

DB only latency 캡처는 아직 없으므로 DB only p50/p95/p99는 확정하지 않는다. 단, cache enabled 모드의 실제 지연시간과 DB fallback QPS는 이번 측정값을 반영한다.

| 시나리오 | 모드 | p50 | p95 | p99 | 처리량 | DB QPS | Redis hit ratio |
|---|---|---:|---:|---:|---:|---:|---:|
| 단건 조회 | DB only | 추가 측정 필요 | 추가 측정 필요 | 추가 측정 필요 | 추가 측정 필요 | 추가 측정 필요 | N/A |
| 단건 조회 | Cache enabled | 2.15ms | 13.04ms | 17.18ms | 583.74 req/s | 27.78/s | 91.98% |
| 다건 조회 | DB only | 추가 측정 필요 | 추가 측정 필요 | 추가 측정 필요 | 추가 측정 필요 | 추가 측정 필요 | N/A |
| 다건 조회 | Cache enabled | 2.76ms | 13.33ms | 29.38ms | 292.08 req/s | 55.54/s | 98.60% |
| Hot/Cold 조회 | DB only | 추가 측정 필요 | 추가 측정 필요 | 추가 측정 필요 | 추가 측정 필요 | 추가 측정 필요 | N/A |
| Hot/Cold 조회 | Cache enabled | 2.13ms | 11.60ms | 16.86ms | 583.73 req/s | 8.06/s | 97.98% |

## 산술 추정

DB only 실측 latency가 아직 없기 때문에 README에는 p99 Before/After를 확정값으로 쓰지 않는다. 다만 같은 요청량이 모두 DB로 직접 흘렀다고 가정하면, cache enabled 모드의 DB fallback 감소 폭은 다음과 같이 추정할 수 있다.

| 시나리오 | 기준 부하 | cache enabled DB fallback | DB fallback 감소 추정 |
|---|---:|---:|---:|
| 단건 조회 | 583.74 req/s | 27.78/s | 약 95.2% 감소 |
| 다건 조회 | 5,841.60 item/s | 55.54/s | 약 99.0% 감소 |
| Hot/Cold 조회 | 583.73 req/s | 8.06/s | 약 98.6% 감소 |

위 감소율은 DB only 실측값이 아니라 요청량 대비 cache fallback metric으로 계산한 산술 추정이다. 최종 PR/README Highlight에는 DB only 모드 실측 결과를 추가한 뒤 확정 문구로 반영한다.

## Metric 계산 방식

### DB QPS

Actuator metric의 테스트 전후 delta로 계산한다.

```text
DB QPS = product.cache.fallback.items{result="requested"} delta / 측정 초
```

조회 명령:

```powershell
Invoke-RestMethod "http://localhost:8080/actuator/metrics/product.cache.fallback.items?tag=result:requested"
```

### Redis Hit Ratio

```text
Redis hit ratio = hit delta / (hit delta + miss delta)
```

조회 명령:

```powershell
Invoke-RestMethod "http://localhost:8080/actuator/metrics/product.cache.read.items?tag=result:hit"
Invoke-RestMethod "http://localhost:8080/actuator/metrics/product.cache.read.items?tag=result:miss"
```

## README Highlight 반영 기준

README에는 DB only와 cache enabled를 모두 측정한 뒤 실제 Before/After 수치만 반영한다.

```md
- 단건 조회 p99 **{DB only p99}ms -> 17.18ms**, DB QPS **{DB only QPS}/s -> 27.78/s** *(k6 측정, `docs/benchmark.md` 참고)*
```

현재 cache enabled p99와 DB fallback QPS는 실제 측정값으로 확보됐다. DB only p99와 DB only QPS 캡처를 추가하면 README Highlight의 Before/After 문구를 확정할 수 있다.

## 추가로 필요한 자료

- DB only 단건/다건/hot-cold k6 결과 캡처 또는 `--summary-export` JSON
- DB only 각 시나리오 테스트 전후 actuator metric 값
  - `product.cache.fallback.items?tag=result:requested`
  - DB only 모드에서는 Redis read metric이 N/A일 수 있음
