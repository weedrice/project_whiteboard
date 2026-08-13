# CORS 호환성 가이드

## 기준

| 항목 | 내용 |
| --- | --- |
| 기준일 | 2026-08-13 |
| 백엔드 설정 | `backend/src/main/java/com/weedrice/whiteboard/global/config/SecurityConfig.java` |
| 프론트 API 클라이언트 | `frontend/src/api/index.ts` |

백엔드는 `app.frontend-url`에 설정된 단일 프론트엔드 origin을 허용하고, credential 포함 요청을 허용한다.

## 현재 허용 값

### Methods

- `GET`
- `POST`
- `PUT`
- `DELETE`
- `OPTIONS`
- `PATCH`

### Request Headers

| 헤더 | 용도 |
| --- | --- |
| `Authorization` | 사용자 JWT 인증 |
| `Content-Type` | JSON, multipart form 요청 |
| `Accept` | 응답 타입 협상 |
| `X-NoviIs-Agent` | Agent/MCP 계열 요청 식별 |
| `X-NoviIs-Internal-Secret` | 내부 보호 요청 식별 |

### Exposed Headers

- `RateLimit-Limit`
- `RateLimit-Remaining`
- `RateLimit-Reset`
- `Retry-After`

`Content-Type`과 `Content-Length`는 CORS safelisted response header이므로 별도 노출 목록에 넣지 않아도 브라우저 JavaScript에서 읽을 수 있다. Rate limit 헤더는 safelist에 없으므로 백엔드가 명시적으로 노출한다.

## 프론트엔드 영향

- 일반 Axios API 요청은 `Authorization`, `Content-Type`, `Accept` 범위 안에서 동작한다.
- 파일 업로드는 `Content-Type: multipart/form-data`를 사용하므로 허용 범위에 포함된다.
- 새로운 커스텀 헤더를 추가하면 `SecurityConfig`의 allowed headers도 같이 갱신해야 한다.
- Agent 전용 헤더는 일반 사용자 화면에서 임의로 추가하지 않는다.

## 확인 체크리스트

- 로그인/회원가입
- JWT 포함 사용자 API
- 파일 업로드
- OAuth callback 후 API 호출
- 알림 SSE stream
- Agent/MCP 요청 헤더가 필요한 경로

## 문제 확인 순서

1. 브라우저 Network 탭에서 실패한 `OPTIONS` preflight 응답을 확인한다.
2. 요청 origin이 `app.frontend-url`과 일치하는지 확인한다.
3. 요청 header가 허용 목록에 있는지 확인한다.
4. 새 header가 필요하면 백엔드 설정과 이 문서를 같은 변경에서 갱신한다.
