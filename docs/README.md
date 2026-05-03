# Product Cache Service

상품 조회 성능과 운영 안정성을 함께 고려해 설계한 멀티 모듈 기반 백엔드 서비스다.

단순 CRUD API가 아니라 Redis 캐시, Cache-Aside, self-healing, 관리자용 캐시 재구축, 이벤트 기반 갱신, 관측성, 장애 대응, API 운영성까지 포함해 실제 운영 환경에서 자주 마주치는 문제를 구조적으로 다루는 데 초점을 맞췄다.

## 핵심 목표

- 반복 조회가 많은 상품 API의 DB 부하를 줄인다.
- 캐시를 단순 성능 보조 수단이 아니라 운영 가능한 데이터 계층으로 관리한다.
- 캐시 일부 유실 상황에서도 DB fallback과 self-healing으로 응답 안정성을 유지한다.
- 재구축 작업의 진행률, 활성 여부, 실패 사유를 추적할 수 있게 한다.
- 이벤트 기반 갱신으로 상품 변경 후 캐시 정합성을 보강한다.
- 메트릭, 요청 ID, 비동기 예외 처리로 운영 중 문제를 관측 가능하게 만든다.
- 멀티 모듈 구조로 API, 유스케이스, 도메인, 인프라 책임을 분리한다.

## 주요 기능

- 상품 단건/다건 조회
- Redis detail cache / runtime cache 분리
- Cache-Aside 기반 DB fallback
- cache miss 시 누락 캐시 자동 복구
- TTL jitter 기반 캐시 만료 분산
- 관리자용 캐시 재구축 요청
- 재구축 작업 상태 조회
- 재구축 중복 실행 방지
- 재구축 요청 수 제한
- Redis 기반 rebuild job store
- keyset chunk 기반 전체 재구축
- 상품 변경 이벤트 기반 캐시 갱신/삭제
- 캐시/재구축/이벤트 메트릭 수집
- Prometheus 스크랩 구성
- Flyway 기반 스키마 변경 관리
- API 요청 추적 ID 응답
- 표준 예외 응답
- 장애 실패 사유 정규화

## 모듈 구조

```text
product-cache-service
├── product-api
├── product-application
├── product-domain
├── product-infrastructure
├── docker
├── docs
└── docker-compose.yml
```

### product-api

외부 요청과 응답을 처리하는 계층이다.

- REST API controller
- request/response DTO
- 입력값 검증
- 표준 예외 응답 변환
- 요청 추적 ID 필터
- 비동기 executor 설정
- 관리자 API 운영 응답 헤더

### product-application

유스케이스 흐름을 조합하는 계층이다.

- 상품 조회 흐름 제어
- detail/runtime 캐시 병합
- DB fallback
- self-healing 캐시 적재
- 캐시 재구축 요청/상태 조회
- keyset chunk 재구축 처리
- 상품 변경 이벤트 처리
- 실패 사유 정규화
- 메트릭 포트 정의

### product-domain

기술 구현과 분리된 핵심 규칙을 담는 계층이다.

- 상품 상태 계산
- 재고 기반 판매 가능 여부 판단
- 도메인 예외와 에러 코드
- 비즈니스 정책 표현

### product-infrastructure

외부 기술 구현을 담당하는 계층이다.

- Redis cache adapter
- Redis rebuild job store
- JPA repository adapter
- Flyway migration
- Spring event publisher/listener
- Micrometer metrics adapter
- Redis TTL policy

## 캐시 전략

상품 캐시는 데이터 성격에 따라 분리한다.

```text
product:detail:{productId}
product:runtime:{productId}
```

detail cache는 상품명, 가격 등 상대적으로 정적인 정보를 저장한다.

runtime cache는 판매 상태, 품절 여부처럼 변경 가능성이 더 높은 상태성 정보를 저장한다.

조회 흐름은 Cache-Aside 기반이다.

1. detail cache와 runtime cache를 조회한다.
2. 두 캐시가 모두 있으면 병합해 응답한다.
3. 하나라도 없으면 DB fallback을 수행한다.
4. DB 조회 결과로 응답을 만들고 누락 캐시를 다시 적재한다.
5. read/write/fallback 메트릭을 기록한다.

TTL에는 jitter를 적용해 특정 시점에 캐시 만료가 몰리는 문제를 완화한다.

## 운영 기능

관리자 API는 캐시 재구축을 비동기 작업으로 접수한다.

```http
POST /admin/cache/products/rebuild
```

응답은 작업 접수 의미에 맞게 `202 Accepted`를 반환한다.

```http
HTTP/1.1 202 Accepted
Location: /admin/cache/products/jobs/{jobId}
Cache-Control: no-store
X-Request-Id: {requestId}
```

작업 상태는 다음 API로 조회한다.

```http
GET /admin/cache/products/jobs/{jobId}
```

상태 응답에는 다음 정보가 포함된다.

- jobId
- status
- totalCount
- processedCount
- progressPercent
- active
- message
- failureReason
- target
- startedAt
- completedAt

전체 재구축은 keyset chunk 방식으로 처리해 전체 ID를 한 번에 메모리에 올리지 않는다.

## 이벤트 기반 갱신

상품 변경 이벤트를 기반으로 캐시를 갱신하거나 삭제한다.

지원하는 변경 타입은 다음과 같다.

```text
UPDATED
DELETED
```

처리 정책은 다음과 같다.

- `UPDATED`: 원본 상품을 다시 조회해 캐시 refresh
- `UPDATED`인데 상품이 없으면 stale cache 방지를 위해 evict
- `DELETED`: DB 조회 없이 evict

이벤트 리스너는 비동기로 동작해 상품 변경 처리 흐름과 캐시 갱신 흐름을 분리한다.

## 관측성

Micrometer 기반 메트릭을 수집하고 Prometheus가 스크랩할 수 있도록 구성했다.

대표 메트릭은 다음과 같다.

```text
product.cache.read.items
product.cache.write.items
product.cache.fallback.items
product.cache.rebuild.jobs
product.cache.rebuild.items
product.cache.rebuild.duration
product.cache.rebuild.chunk.items
product.cache.rebuild.chunk.duration
product.cache.event.handled
```

Kafka consumer lag 계산은 샘플링 기반으로 제어한다.

```properties
queue.kafka.lag-sample-interval-ms=5000
```

## 장애 대응

장애 대응 정책은 다음 기준을 따른다.

- 재구축 실패는 실패 메트릭과 실패 사유를 남기고 job 상태를 `FAILED`로 저장한다.
- 이벤트 처리 실패는 실패 메트릭과 로그를 남긴 뒤 예외를 다시 던진다.
- 비동기 예외는 공통 `AsyncUncaughtExceptionHandler`에서 로깅한다.
- 실패 사유는 `FailureReasonBuilder`를 통해 예외 타입과 메시지 기준으로 정규화한다.

## API 운영성

모든 API 응답에는 `X-Request-Id`가 포함된다.

- 클라이언트가 전달한 request id가 있으면 그대로 반환한다.
- 없으면 서버가 UUID 기반 request id를 생성한다.
- request id는 MDC에 저장되어 로그 연계에 사용할 수 있다.

잘못된 UUID 같은 경로 파라미터 타입 변환 실패는 서버 오류가 아니라 400 입력 오류로 응답한다.

## 실행 방법

Docker Desktop 환경에서 전체 스택을 실행할 수 있다.

```bash
docker compose up -d --build
```

설정 검증은 다음 명령으로 확인할 수 있다.

```bash
docker compose config
```

## 테스트

전체 테스트는 다음 명령으로 실행한다.

```bash
./gradlew test
```

Windows PowerShell에서는 다음 명령을 사용한다.

```powershell
.\gradlew.bat test
```

테스트는 계층별 책임에 맞춰 분리한다.

- domain test: 상품 상태 계산, 판매 가능 여부 등 순수 규칙 검증
- application test: 캐시 조회 흐름, DB fallback, self-healing, 재구축, 이벤트 처리 검증
- api test: controller, 예외 응답, 요청 ID, 관리자 API 응답 헤더 검증
- infrastructure test: Redis adapter, job store, TTL policy, metrics, event adapter 검증
- JPA test: 저장, 조회, 매핑, 제약조건 검증
- Testcontainers integration test: 실제 Redis 저장/조회/TTL/key 삭제 검증

## 문서

상세 설계와 운영 정책은 별도 문서로 정리했다.

- [API 운영성 기준](./api-operability.md)
- [장애 대응 정책](./failure-response-policy.md)
- [설계 상세 문서](https://www.notion.so/Product-Cache-Service-32dd2aef6d3180659e80c88cccd7a58c)

## 추가 개선 필요 항목

- 이벤트 실패 재처리 정책
- Outbox 기반 이벤트 발행
- Dead Letter Queue(DLQ)
- 관리자 API 인증/인가
- 관리자 API rate limit
- access log 구조화
- 재구축 병렬 처리
- 실패 chunk 재시도
- 운영 알림 정책
