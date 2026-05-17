# Benchmark

상품 조회 캐시 적용 효과를 DB only 모드와 cache enabled 모드로 비교하기 위한 부하 테스트 기록이다.

현재 문서에는 전달받은 k6 콘솔 캡처 기준의 **cache enabled 1차 측정값**을 우선 반영했다. README Highlight에 Before/After 수치를 확정하려면 같은 환경에서 DB only 결과와 actuator metric delta를 추가로 수집해야 한다.

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

## 시나리오

| 시나리오 | 스크립트 | 목표 부하 |
|---|---|---|
| 단건 조회 | `loadtest/k6/product-single.js` | 최대 1,000 iters/s, 6분 실행 |
| 다건 조회 | `loadtest/k6/product-batch.js` | 20 ids/request, 최대 500 iters/s, 6분 실행 |
| Hot/Cold 조회 | `loadtest/k6/product-hot-cold.js` | 90% hot key, 10% cold key, 최대 1,000 iters/s, 6분 실행 |

## Cache Enabled 1차 측정 결과

| 시나리오 | Checks | 실패율 | avg | p50 | p90 | p95 | p99 | max | 처리량 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 단건 조회 | 210,149 | 0.00% | 3.28ms | 2.11ms | 6.41ms | 11.75ms | 미수집 | 90.20ms | 583.74 req/s |
| 다건 조회 | 105,148 | 0.00% | 3.92ms | 2.67ms | 5.68ms | 11.21ms | 미수집 | 122.63ms | 292.07 req/s |
| Hot/Cold 조회 | 210,144 | 0.00% | 2.94ms | 2.08ms | 4.68ms | 9.61ms | 미수집 | 63.38ms | 583.73 req/s |

> k6 기본 콘솔 출력에는 p99가 표시되지 않아 이번 캡처 기준 p99는 미수집으로 둔다. `summaryTrendStats`에 `p(99)`를 추가했으므로 다음 실행부터 콘솔과 summary export에서 p99를 함께 확인할 수 있다.

## DB Only / Cache Enabled 비교표

동일 머신, 동일 데이터셋, 동일 k6 옵션으로 DB only와 cache enabled를 각각 실행한 뒤 아래 표를 채운다.

| 시나리오 | 모드 | p50 | p95 | p99 | 처리량 | DB QPS | Redis hit ratio |
|---|---|---:|---:|---:|---:|---:|---:|
| 단건 조회 | DB only | 추가 측정 필요 | 추가 측정 필요 | 추가 측정 필요 | 추가 측정 필요 | 추가 측정 필요 | N/A |
| 단건 조회 | Cache enabled | 2.11ms | 11.75ms | 추가 측정 필요 | 583.74 req/s | 추가 측정 필요 | 추가 측정 필요 |
| 다건 조회 | DB only | 추가 측정 필요 | 추가 측정 필요 | 추가 측정 필요 | 추가 측정 필요 | 추가 측정 필요 | N/A |
| 다건 조회 | Cache enabled | 2.67ms | 11.21ms | 추가 측정 필요 | 292.07 req/s | 추가 측정 필요 | 추가 측정 필요 |
| Hot/Cold 조회 | DB only | 추가 측정 필요 | 추가 측정 필요 | 추가 측정 필요 | 추가 측정 필요 | 추가 측정 필요 | N/A |
| Hot/Cold 조회 | Cache enabled | 2.08ms | 9.61ms | 추가 측정 필요 | 583.73 req/s | 추가 측정 필요 | 추가 측정 필요 |

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
- 단건 조회 p99 **{DB only p99}ms -> {Cache enabled p99}ms**, 1k RPS 시 DB QPS **-{감소율}%** *(k6 측정, `docs/benchmark.md` 참고)*
```

현재 캡처만으로는 DB only p99와 DB QPS 감소율이 없으므로 README Highlight의 기존 예시 수치는 확정값으로 사용하지 않는다.

## 추가로 필요한 자료

- DB only 단건/다건/hot-cold k6 결과 캡처 또는 `--summary-export` JSON
- cache enabled 재측정 결과 중 p99 포함 캡처 또는 `--summary-export` JSON
- 각 시나리오 테스트 전후 actuator metric 값
  - `product.cache.fallback.items?tag=result:requested`
  - `product.cache.read.items?tag=result:hit`
  - `product.cache.read.items?tag=result:miss`
