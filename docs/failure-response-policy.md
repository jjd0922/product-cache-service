# 장애 대응 정책

## 기본 원칙

- 캐시 갱신 실패는 원본 데이터 변경 실패로 취급하지 않는다.
- 비동기 작업 실패는 호출자에게 직접 반환되지 않으므로 로그와 메트릭으로 남긴다.
- 운영 저장소에 남기는 실패 사유는 예외 타입과 메시지를 기준으로 정규화한다.
- 실패 사유는 과도하게 길어지지 않도록 제한한다.

## 재빌드 작업

- 재빌드 요청이 유효하지 않으면 작업을 실패 처리한다.
- 재빌드 중 예외가 발생하면 작업 상태를 `FAILED`로 저장한다.
- 실패 사유는 `FailureReasonBuilder`를 통해 정규화한다.
- 실패 메트릭은 `product.cache.rebuild.jobs{result="error"}`로 기록한다.

## 상품 변경 이벤트

- 유효하지 않은 이벤트 command는 처리하지 않는다.
- `UPDATED` 이벤트는 원본 상품을 다시 조회한 뒤 캐시를 갱신한다.
- 원본 상품이 없으면 stale cache 방지를 위해 캐시를 삭제한다.
- `DELETED` 이벤트는 DB 조회 없이 캐시를 삭제한다.
- 이벤트 처리 실패는 `product.cache.event.handled{result="error"}`로 기록하고 예외를 다시 던진다.
- 이벤트 리스너는 Spring Retry로 최대 3회 재시도한다.
- 재시도 backoff는 기본 1초에서 시작하고 multiplier 기본값은 2.0이다.
- 최종 실패 이벤트는 Redis Stream DLQ에 저장한다.
- DLQ 저장은 `product.cache.event.dlq`, retry attempt는 `product.cache.event.retry`로 기록한다.
- 운영자는 관리자 API로 DLQ 이벤트를 조회하고 재처리할 수 있다.
- 이벤트 처리는 비동기이므로 상품 변경 직후 짧은 stale read window가 존재한다. TTL, retry/DLQ, 관리자 rebuild로 최종 수렴시키며, 쓰기 직후 강한 정합성이 필요한 조회는 DB 직접 조회 또는 write-through 정책을 별도로 적용해야 한다.
- DLQ 재처리 중 다시 실패하면 `delete`가 실행되지 않으므로 해당 이벤트는 DLQ에 그대로 유지된다. 무한 자동 재시도는 하지 않으며, 운영자가 원인을 제거한 뒤 재처리 API를 다시 호출해야 한다.
- 현재 구현은 DLQ 재처리 실패 전용 `result="error"` 태그 메트릭을 별도로 기록하지 않는다. 재처리 실패는 관리자 API 실패 응답과 구조화 로그로 확인하며, `product.cache.event.dlq{result="error"}` 형태의 전용 메트릭은 후속 보강 대상이다.

## 비동기 예외

- `@Async` 기반 작업은 공통 `AsyncUncaughtExceptionHandler`에서 예외를 로깅한다.
- 로그에는 메서드명, 파라미터 개수, 정규화된 실패 사유를 남긴다.
- 비동기 예외 핸들러는 장애 관측 목적이며, 이벤트 재처리 책임은 Retry와 DLQ가 담당한다.

## Redis 캐시 장애

- detail/runtime/not-found cache adapter의 Redis 호출은 Circuit Breaker로 감싼다.
- 실패율 임계치에 도달하면 circuit이 open 상태가 되고 Redis 호출을 빠르게 우회한다.
- open 상태에서는 DB fallback으로 degrade한다.
- DB fallback은 semaphore Bulkhead로 보호해 동시 DB 호출 수를 제한한다.
- Bulkhead 포화 시 `DbFallbackRejectedException`으로 실패시키고 `product.cache.fallback.rejected`를 기록한다.
- Redis가 복구되면 half-open 검증 호출 이후 closed 상태로 자동 복귀한다.

운영 임계치:

| 항목 | 현재 값 |
|---|---:|
| Circuit Breaker 실패율 임계치 | 50% |
| Circuit Breaker sliding window | 20 calls |
| Circuit Breaker minimum calls | 5 calls |
| Open -> half-open 전환 대기 | 30s |
| Half-open 허용 호출 수 | 3 calls |
| HikariCP maximum-pool-size | 30 |
| HikariCP minimum-idle | 10 |
| HikariCP connection-timeout | 3000ms |
| DB fallback Bulkhead 동시 호출 한도 | 20 |
| Bulkhead 대기 시간 | 0ms, 즉시 reject |
| Negative cache TTL | 60s |
| Spring Retry maxAttempts | 3 |
| Spring Retry initial delay | 1s |
| Spring Retry multiplier | 2.0 |

## Negative Cache

- DB에도 존재하지 않는 productId는 `product:v1:notfound:{productId}` 형태의 not-found marker로 캐싱한다.
- not-found marker hit는 DB fallback 없이 즉시 미존재로 처리한다.
- 신규 상품 등록 지연을 줄이기 위해 TTL은 짧게 유지한다. 현재 기본값은 60초다.
- not-found hit는 `product.cache.notfound.hits`로 기록한다.

## 후속 보강 후보

- 장애 유형별 알림 임계치
- 재빌드 작업 강제 종료 및 재시작 정책
- Redis Stream DLQ는 Redis persistence 설정에 영향을 받으므로 운영 환경에서는 AOF 활성화 또는 DB 기반 DLQ 전환을 검토한다.
