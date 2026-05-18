# Product Cache Service

[![CI](https://github.com/jongdae/product-cache-service/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/jongdae/product-cache-service/actions/workflows/ci.yml)
[![Coverage](https://codecov.io/gh/jongdae/product-cache-service/branch/main/graph/badge.svg)](https://codecov.io/gh/jongdae/product-cache-service)
[![Java](https://img.shields.io/badge/Java-17-orange)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green)]()
[![Redis](https://img.shields.io/badge/Redis-7.x-red)]()

상품 조회 성능과 운영 안정성을 함께 다루는 모듈 기반 백엔드 서비스입니다.

상세 설계, 실행 방법, 관측성, 부하 테스트 결과는 [docs/README.md](./docs/README.md)를 참고하세요.

## Highlights

- k6 부하 테스트 기준 단건 조회 p99 `1318.34ms -> 17.19ms`, DB 부하 `-95.1%`
- 단건/Hot 조회는 p99 개선, 다건 조회는 DB 부하 `-99.0%` 대신 latency trade-off 발생
- Redis 장애 시 Circuit Breaker + DB fallback Bulkhead로 DB 보호
- Hot key TTL 동시 만료 시 로컬 single-flight + Redis 분산 락으로 DB 중복 조회 억제
- 이벤트 처리 실패 시 Retry 이후 Redis Stream DLQ에 보관하고 관리자 API로 재처리
- Prometheus, Grafana, Jaeger, JSON 로그로 metrics, traces, logs 관측성 구성

## Quick Start

```bash
docker compose up -d
./gradlew :product-api:bootRun
```

```text
API         http://localhost:8080
Grafana     http://localhost:3000
Prometheus  http://localhost:9090
Jaeger      http://localhost:16686
```
