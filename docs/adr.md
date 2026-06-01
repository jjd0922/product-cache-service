# ADR - Architecture Decision Records

## ADR-001. Detail cache와 runtime cache를 분리한다

상태: Accepted

### Context

상품 데이터는 상품명, 가격처럼 비교적 정적인 정보와 판매 상태, 품절 여부처럼 자주 바뀌는 정보가 함께 섞여 있다. 단일 캐시 키로 관리하면 단순하지만, 변경 빈도가 다른 데이터를 같은 TTL과 같은 갱신 정책으로 다뤄야 한다.

### Decision

상품 캐시를 `detail`과 `runtime`으로 분리한다.

```text
product:v1:detail:{productId}
product:v1:runtime:{productId}
```

### Consequences

- 변경 빈도에 맞춰 TTL과 갱신 정책을 분리할 수 있다.
- cache read/write metric을 데이터 성격별로 관측할 수 있다.
- 부분 write 실패 가능성이 생긴다.
- 부분 실패 시 양쪽 키를 모두 evict하고 다음 read에서 DB fallback으로 복구한다.

## ADR-002. Local single-flight와 Redis 분산 락을 함께 사용한다

상태: Accepted

### Context

Hot key가 동시에 만료되면 동일 productId 요청이 한꺼번에 DB fallback으로 진입할 수 있다. 단일 인스턴스에서는 로컬 in-flight dedup만으로 충분하지만, 운영 환경에서는 여러 인스턴스가 같은 키를 동시에 처리할 수 있다.

### Decision

인스턴스 내부 중복은 local single-flight로 줄이고, 인스턴스 간 중복은 Redis 분산 락으로 제한한다.

### Consequences

- 동일 키 cache miss 상황에서 DB 조회를 1회에 가깝게 수렴시킬 수 있다.
- Redis 의존성이 증가한다.
- lock lease time과 대기 전략을 운영 지표에 맞춰 조정해야 한다.
- Redis 장애 시에는 Circuit Breaker와 DB fallback bulkhead가 보호선 역할을 한다.

## ADR-003. Redis 기반 rebuild job store를 사용한다

상태: Accepted

### Context

캐시 재구축은 비동기 작업으로 처리되며, 운영자는 진행률과 실패 사유를 조회해야 한다. 인메모리 상태 저장은 구현이 단순하지만 애플리케이션 재시작과 다중 인스턴스 환경에서 상태가 유실되거나 불일치할 수 있다.

### Decision

재구축 job 상태를 Redis에 저장한다.

### Consequences

- 재시작 후에도 job 상태를 일정 기간 조회할 수 있다.
- 여러 인스턴스에서 같은 job 상태를 공유할 수 있다.
- Redis 장애 시 job 상태 조회와 갱신이 영향을 받는다.
- 장기 보관이 필요한 감사 로그는 별도 저장소 도입을 검토해야 한다.

## ADR-004. 상품 변경 이벤트는 비동기 처리하고 retry와 DLQ로 회수한다

상태: Accepted

### Context

상품 변경 API의 주요 책임은 원본 데이터 변경이다. 캐시 갱신을 동기 처리하면 캐시 계층 장애가 상품 변경 흐름까지 전파된다. 반대로 완전 비동기로 처리하면 일시적인 stale cache 가능성이 생긴다.

### Decision

상품 변경 이벤트는 비동기로 처리하고, 실패 시 retry 후 Redis Stream 기반 DLQ에 적재한다.

### Consequences

- 상품 변경 흐름과 캐시 갱신 흐름의 장애를 격리할 수 있다.
- 일시적으로 stale cache가 노출될 수 있다.
- 실패 이벤트는 DLQ에서 확인하고 관리자 API로 재처리한다.
- 이벤트 처리 성공률과 DLQ 증가량을 운영 지표로 추적한다.

## ADR-005. Redis 장애 시 graceful degradation을 선택한다

상태: Accepted

### Context

Redis는 상품 조회 성능을 크게 개선하지만, Redis 장애 중 모든 요청을 DB로 무제한 우회시키면 DB까지 연쇄 장애가 날 수 있다.

### Decision

Redis 장애는 Circuit Breaker로 감지하고, DB fallback은 Bulkhead로 동시 호출 수를 제한한다.

### Consequences

- Redis 장애 중에도 일부 요청은 DB fallback으로 처리된다.
- DB 보호를 위해 일부 요청은 빠르게 실패할 수 있다.
- fallback rejection은 장애 알림과 운영 점검 대상이다.
- 시스템 목표는 무조건 성공이 아니라 핵심 저장소 보호와 빠른 복구다.

## ADR-006. 관측성은 metric, log, trace 3축으로 구성한다

상태: Accepted

### Context

캐시 서비스의 장애는 latency 증가, hit ratio 하락, fallback 증가, 이벤트 처리 실패처럼 여러 신호가 함께 나타난다. 단일 관측 수단만으로는 원인을 빠르게 좁히기 어렵다.

### Decision

Micrometer/Prometheus metric, JSON structured log, OpenTelemetry trace를 모두 사용한다.

### Consequences

- 운영 지표와 장애 분석을 같은 지표 체계로 연결할 수 있다.
- requestId를 기준으로 응답, 로그, trace를 추적할 수 있다.
- 운영 복잡도와 저장 비용이 증가한다.
- 민감정보 마스킹과 trace sampling 정책을 함께 관리해야 한다.
