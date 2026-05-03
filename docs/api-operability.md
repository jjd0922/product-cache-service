# API 운영성 기준

## 요청 추적

- 모든 API 응답은 `X-Request-Id` 헤더를 포함한다.
- 클라이언트가 `X-Request-Id`를 보내면 같은 값을 응답에 반환한다.
- 클라이언트가 보내지 않으면 서버가 UUID 기반 요청 ID를 생성한다.
- 요청 ID는 MDC의 `requestId` 키에 저장되어 로그 연계에 사용할 수 있다.

## 관리자 캐시 API

### 재빌드 요청

- `POST /admin/cache/products/rebuild`
- 재빌드는 비동기 작업으로 접수되므로 `202 Accepted`를 반환한다.
- 응답 `Location` 헤더는 작업 상태 조회 API를 가리킨다.
- 운영성 보장을 위해 `Cache-Control: no-store`를 반환한다.

### 재빌드 작업 조회

- `GET /admin/cache/products/jobs/{jobId}`
- 작업 상태 응답은 캐시하지 않는다.
- `Cache-Control: no-store`와 `Pragma: no-cache`를 반환한다.

## 오류 응답

- API 오류는 공통 `ApiResponse` failure 포맷을 사용한다.
- 요청 본문 파싱 실패는 `COMMON-400-JSON`으로 응답한다.
- Bean Validation 실패는 `COMMON-400-VALIDATION`으로 응답한다.
- 경로/쿼리 파라미터 타입 변환 실패는 `COMMON-400`으로 응답한다.
- 예상하지 못한 서버 예외만 `COMMON-500`으로 응답한다.

## 후속 보강 후보

- 요청/응답 access log 필터
- 운영자 전용 API 인증/인가
- 관리자 API rate limit
- API별 SLO 문서화
