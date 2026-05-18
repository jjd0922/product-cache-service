# API 운영성 기준

## 요청 추적

- 모든 API 응답은 `X-Request-Id` 헤더를 포함한다.
- 클라이언트가 `X-Request-Id`를 보내면 같은 값을 응답에 반환한다.
- 클라이언트가 보내지 않으면 서버가 UUID 기반 요청 ID를 생성한다.
- 요청 ID는 MDC의 `requestId` 키와 trace baggage에 저장되어 로그·trace 연계에 사용할 수 있다.
- `X-User-Id`가 있으면 MDC의 `userId` 키에 저장한다.

## 관리자 캐시 API

관리자 API는 `/admin/**` 전용 Spring Security filter chain으로 보호한다.

- 인증 방식: HTTP Basic Auth
- 계정 주입: `PRODUCT_ADMIN_USERNAME`, `PRODUCT_ADMIN_PASSWORD`
- 권한: `ADMIN`
- 인증 실패: `401 AUTHENTICATION_REQUIRED`
- 권한 부족: `403 ACCESS_DENIED`
- 일반 상품 조회 API는 별도 filter chain에서 permit all로 유지한다.

### 재빌드 요청

- `POST /admin/cache/products/rebuild`
- 재빌드는 비동기 작업으로 접수되므로 `202 Accepted`를 반환한다.
- 응답 `Location` 헤더는 작업 상태 조회 API를 가리킨다.
- 운영성 보장을 위해 `Cache-Control: no-store`를 반환한다.

### 재빌드 작업 조회

- `GET /admin/cache/products/jobs/{jobId}`
- 작업 상태 응답은 캐시하지 않는다.
- `Cache-Control: no-store`와 `Pragma: no-cache`를 반환한다.

### 이벤트 DLQ 조회

- `GET /admin/cache/products/events/dlq?limit=100`
- Redis Stream DLQ에 남은 실패 이벤트를 최신순 제한 개수만큼 조회한다.
- 응답은 캐시하지 않는다.
- `Cache-Control: no-store`와 `Pragma: no-cache`를 반환한다.

### 이벤트 DLQ 재처리

- `POST /admin/cache/products/events/dlq/{eventId}/reprocess`
- DLQ에 저장된 이벤트를 다시 처리하고 `202 Accepted`를 반환한다.
- 재처리에 성공한 이벤트는 DLQ에서 제거된다.
- 응답은 캐시하지 않는다.

## 오류 응답

- API 오류는 공통 `ApiResponse` failure 포맷을 사용한다.
- 요청 본문 파싱 실패는 `COMMON-400-JSON`으로 응답한다.
- Bean Validation 실패는 `COMMON-400-VALIDATION`으로 응답한다.
- 경로/쿼리 파라미터 타입 변환 실패는 `COMMON-400`으로 응답한다.
- 상품 미존재는 `PRODUCT-404`로 응답한다.
- 예상하지 못한 서버 예외만 `COMMON-500`으로 응답한다.

## 로그 운영 기준

- 기본 로그 포맷은 JSON이며 `local` profile에서는 텍스트 로그를 사용한다.
- MDC에는 `requestId`, `userId`를 포함한다.
- 이메일, 전화번호, Authorization 헤더, password 패턴은 로그 출력 시 마스킹한다.

## 후속 보강 후보

- 관리자 API rate limit
- API별 SLO 문서화
