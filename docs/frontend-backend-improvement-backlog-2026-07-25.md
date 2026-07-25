# Frontend·Backend 개선 backlog

## 기준

- 기준일: 2026-07-25
- 범위: `backend/` 도메인·global 계층, `frontend/src` 전체, 두 계층을 잇는 wire 계약과 배포 설정
- **제외 도메인**: `domain/agent`, `domain/ad`. 두 도메인은 소유 주체와 계약 경계가 달라 이번 검토 대상에서 뺐다. 제외에 따라 근거가 바뀐 항목은 A1과 F1이며 각 절에 명시했다.
- 방법: 정적 코드 검토. 런타임 재현이나 부하 시험은 수행하지 않았다.
- 선행 문서: [Frontend–Backend 연결 계약 감사](./frontend-backend-contract-audit-2026-07-14.md)에서 이미 해소한 항목은 중복 기재하지 않고, 그 감사가 남긴 구조적 빈틈과 감사 범위 밖 영역만 다룬다.
- 이 문서는 기록용 backlog이며, 항목별 코드 변경은 포함하지 않는다.

## 요약

| ID | 항목 | 영역 | 우선순위 |
| --- | --- | --- | --- |
| A1 | boolean wire 이름 가드가 일부 DTO만 덮는다 | 계약 | 상 |
| A2 | rate limit 응답 헤더를 아무도 읽지 않는다 | 계약 | 상 |
| A3 | SSE 이벤트 이름에 단일 출처와 계약 테스트가 없다 | 계약 | 중 |
| A4 | `C008`이 두 가지 payload 형태를 가진다 | 계약 | 중 |
| A5 | 대상별 업로드 제한이 클라이언트에만 존재한다 | 계약 | 중 |
| A6 | 응답 정규화 shim이 6곳에 흩어져 있다 | 계약 | 중 |
| A7 | `PageResponseRaw`에 실패 모드가 없다 | 계약 | 하 |
| B1 | `validationErrorResponse`의 파라미터 2개가 미사용이다 | 백엔드 | 하 |
| B2 | `MessageSource`가 시스템 로케일로 fallback한다 | 백엔드 | 하 |
| C1 | 백엔드 에러 코드 리터럴이 6곳에 분산되어 있다 | 프론트엔드 | 하 |
| D1 | HSTS `max-age`가 1일이다 | 배포 | 하 |
| E1 | 스케줄러 timezone 지정이 일관되지 않다 | 실행 환경 | 중 |
| E2 | 프론트의 타임스탬프 해석이 timezone-naive하다 | 실행 환경 | 상 |
| **E3** | **백엔드 내부에 시간 기준 두 개가 공존한다** | **실행 환경** | **최상** |
| F1 | `PageRequestUtils` 오버로드의 두 번째 인자 의미가 충돌한다 | 내부 API | 하 |
| F2 | 익명 캐시가 in-process라 scale-out의 선결 과제다 | 실행 환경 | 하 |

E1·E2·E3은 하나의 뿌리(고정되지 않은 JVM timezone)에서 갈라진다. **E1을 `TZ` 환경변수로 해결하면 E2·E3이 함께 악화되므로** 세 항목은 반드시 함께 판단한다. E3은 이 문서에서 유일하게 **현재 동작에 영향이 확인된** 항목이다.

## A. 계약 지점

### A1. boolean wire 이름 가드가 일부 DTO만 덮는다

**현상**

Jackson은 Lombok이 생성한 `isXxx()` getter에서 `is` 접두사를 제거하므로, `private boolean isNotice`는 어노테이션이 없으면 wire에서 `notice`가 된다. 저장소는 이 동작을 legacy 계약으로 확정하고 `ApiWireContractSerializationTest`와 `backend/API명세서.md`의 "DTO별 boolean wire 이름" 표로 고정해 두었다.

문제는 고정 대상이 **손으로 나열한 6개 DTO**뿐이라는 점이다. agent·ad를 제외하고 전체 DTO를 다시 훑으면 `isXxx` boolean 필드는 10개이며, 그중 9개는 계약 테스트가 덮고 있고 다음 1개만 양쪽 어디에도 없다.

| DTO | 필드 | 실제 wire | 테스트 | 문서 |
| --- | --- | --- | --- | --- |
| `LoginResponse.UserInfo` | `isEmailVerified` | `isEmailVerified` | 없음 | 없음 |

이 필드는 명시적 getter에 `@JsonProperty`가 붙어 있어 wire 이름 자체는 어긋나지 않는다. 즉 **지금 깨진 필드는 없다.** 다만 세 번째 표기 방식을 쓰면서 테스트와 문서 어디에도 등재되지 않아, 이 스타일이 허용되는지 여부가 코드로 표현되어 있지 않다.

현재 세 가지 표기 방식이 공존한다.

1. 필드 그대로 — Jackson이 `is` 제거 (`PostSummary.isNotice`)
2. 필드에 `@JsonProperty` — `is` 유지 (`PostResponse.isNotice`)
3. 명시적 getter에 `@JsonProperty` — `is` 유지 (`LoginResponse.java:27`)

`PostSummary` 한 클래스 안에서도 `isSpoiler`·`isSecret`·`isBlinded`는 `is`를 유지하고 `isNotice`·`isNsfw`·`isLiked`·`isScrapped`·`isSubscribed`는 떨어진다.

**영향**

지금 깨진 화면은 없다. 이 항목의 값어치는 현재 결함이 아니라 **재발 방지**에 있다. 새 DTO에 `boolean isXxx` 필드를 추가할 때 어느 규칙을 따를지 코드가 알려주지 않고, 어긋나도 빌드가 통과한다. 프론트가 해당 필드를 읽으면 값은 조용히 `undefined`가 되어 falsy로 동작한다.

**제안**

손으로 나열하는 테스트를 리플렉션 기반 스캔으로 교체한다. `*/dto/*` 하위 클래스를 순회하며 `boolean is[A-Z]*` 필드를 전부 수집하고, 각 필드가 다음 중 하나를 만족하지 못하면 실패시킨다.

- `@JsonProperty`가 필드 또는 getter에 붙어 있다
- 명시적 legacy 허용 목록에 등재되어 있다

허용 목록은 기존 6개 DTO와 `LoginResponse.UserInfo`로 시작한다. 스캔 대상에서 agent·ad를 제외할지는 두 도메인 소유 주체와 협의해 정한다. 이렇게 하면 신규 DTO는 규칙을 따르거나 목록에 의식적으로 등재하는 것 외의 선택지가 없어진다. 이후 `API명세서.md`의 표는 허용 목록에서 생성하거나, 최소한 목록과 표의 일치를 같은 테스트에서 검증한다.

### A2. rate limit 응답 헤더를 아무도 읽지 않는다

**현상**

백엔드는 `RateLimitHeaderWriter`로 허용·거부 양쪽 응답에 `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset`, `Retry-After`를 기록하고 `RateLimitInterceptorTest`로 검증한다. 그런데 이 값은 어디에서도 소비되지 않는다.

- `frontend/src/queryClient.ts:66`은 429에 대해 서버가 알려준 대기 시간을 무시하고 `Math.min(1000 * 2 ** attemptIndex, 30000)` 고정 백오프를 쓴다.
- `SecurityConfig.java:179`의 `setExposedHeaders`는 `Content-Type`, `Content-Length`만 담고 있다. 두 헤더는 이미 CORS safelisted 응답 헤더이므로 이 설정은 사실상 아무 것도 노출하지 않으며, rate limit 헤더는 브라우저 JS가 읽을 수 없다.

**영향**

운영은 nginx가 `noviis.kr` 한 오리진에서 정적 자산과 `/api/`를 함께 서비스하는 same-origin 구성이라 지금 장애로 이어지지는 않는다. 그러나 `VITE_API_BASE_URL`로 백엔드를 직접 지정하는 개발 구성이나 향후 다른 오리진 클라이언트에서는 헤더를 읽을 방법이 없다. 그리고 same-origin에서도 클라이언트가 값을 사용하지 않으므로, 서버가 "3초 뒤 재시도"라고 알려줘도 클라이언트는 1초·2초·4초로 눈감고 재시도한다.

**제안**

- `setExposedHeaders`에 `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset`, `Retry-After`를 추가한다.
- `queryClient`의 `retryDelay`를 429일 때 `Retry-After`를 우선 사용하고, 없을 때만 기존 지수 백오프로 떨어지도록 바꾼다.
- 두 계층이 함께 움직이는 변경이므로 백엔드 헤더 이름 상수와 프론트 소비 지점을 한 커밋으로 묶는다.

### A3. SSE 이벤트 이름에 단일 출처와 계약 테스트가 없다

**현상**

REST 응답 envelope는 `ApiWireContractSerializationTest`로 고정되어 있지만, SSE 채널에는 대응하는 장치가 없다. 이벤트 이름이 양쪽에 문자열 리터럴로만 존재한다.

백엔드가 실제로 보내는 이름 (`NotificationSseEmitterRegistry`):

- `connect`
- `notification`
- `comment`
- `comment-topic-invalidated`
- `comment-topic-access-revoked`

프론트가 분기 처리하는 이름 (`notificationStreamController.ts`):

- `connect`, `comment`, `comment-topic-invalidated`, `comment-topic-access-revoked`, `notification`
- **`message`** — `notificationStreamController.ts:124`에서 `notification`과 동일하게 처리하지만, 백엔드 전 범위에서 이 이름으로 보내는 코드를 찾지 못했다.

**영향**

`message` 분기는 현재 도달하지 않는 코드다. 제거된 기능의 잔재이거나 연결되지 않은 기능으로 보이며, 어느 쪽인지 코드만으로는 판별되지 않는다. 더 중요한 문제는 이름 하나를 백엔드에서 바꿔도 프론트 빌드·테스트가 아무 신호를 주지 않는다는 점이다.

**제안**

- `message` 분기의 의도를 먼저 확인한다. 미연결 기능이면 backlog로 분리하고, 잔재면 제거한다.
- 이벤트 이름을 백엔드 상수 클래스로 모으고, REST의 wire 계약 테스트와 같은 층위에서 "상수 목록과 실제 `SseEmitter.event().name(...)` 호출이 일치한다"를 검증한다.
- 프론트는 이름 집합을 union 타입으로 선언해 분기 누락과 오타를 타입 검사에서 잡는다.

### A4. `C008`이 두 가지 payload 형태를 가진다

**현상**

같은 에러 코드가 검증 경로에 따라 다른 몸통을 반환한다.

| 경로 | 예외 | `details` |
| --- | --- | --- |
| `@Valid @RequestBody` | `MethodArgumentNotValidException` | 있음 (`{field: [message]}`) |
| `@Validated` 쿼리 파라미터 | `ConstraintViolationException`, `HandlerMethodValidationException` | 없음 |
| 파라미터 타입 불일치 | `MethodArgumentTypeMismatchException` | 없음 |
| 파라미터 누락 | `MissingServletRequestParameterException` | 없음 |
| body 파싱 실패 | `HttpMessageNotReadableException` | 없음 |

`BoardController`는 `@Validated`에 `@Size`·`@Pattern`·`@NotBlank`를 `@RequestParam`에 걸고 있어 두 번째 경로가 실제로 도달한다.

**영향**

`frontend/src/api/errorHandling.ts`의 400 분기는 `isValidationErrors(apiError?.details)` 여부로 갈린다. `details`가 없는 경로에서는 필드 단위 표시 없이 일반 요약 메시지로 격하되며, 사용자는 어느 파라미터가 문제인지 알 수 없다. 프론트 타입 `ErrorResponse.details`도 optional이라 이 차이를 표현하지 못한다.

**제안**

두 방향 중 하나를 택한다.

1. `ConstraintViolationException`과 `HandlerMethodValidationException`에서 위반 정보를 `{파라미터명: [메시지]}`로 변환해 `details`를 채운다. 프론트 변경 없이 필드 단위 표시가 동작한다.
2. `details`를 body 검증 전용으로 못 박고, 파라미터 검증에는 별도 에러 코드를 부여해 프론트가 형태를 구분할 수 있게 한다.

1번이 프론트 영향이 없고 기존 envelope를 유지하므로 우선한다. 어느 쪽이든 `API명세서.md`에 `C008`의 payload 형태를 명시한다.

### A5. 대상별 업로드 제한이 클라이언트에만 존재한다

**현상**

업로드 정책이 양쪽에 각각 정의되어 있고 값이 다르다.

| 대상 | 프론트 (`imageUploadPolicy.ts`) | 백엔드 (`FileUploadValidationPolicy.java`) |
| --- | --- | --- |
| 게시글 에디터 이미지 | 10 MiB | 10 MB |
| 스페이스 아이콘 | **2 MiB** | 10 MB |
| 프로필 이미지 | 10 MiB + **100×100 제한** | 10 MB, 16384×16384 / 5천만 픽셀 |

백엔드 `MAX_FILE_SIZE`는 업로드 대상과 무관하게 단일 값이며, 대상별 구분이 없다.

**영향**

UI를 거치지 않는 호출은 스페이스 아이콘으로 10MB 이미지를, 프로필 이미지로 16384×16384 이미지를 올릴 수 있다. 프론트의 2MiB·100×100 제한은 사용자 편의 장치일 뿐 강제력이 없다.

백엔드 자체 검증은 견고하다. magic byte로 실제 형식을 판별하고, 선언 MIME·확장자와 교차 검증하며, 픽셀 폭탄을 차단한다. 부족한 것은 보안 검증이 아니라 **대상별 정책의 서버 측 부재**다.

부수적으로 `spring.servlet.multipart.max-file-size: 10MB`와 `FileUploadValidationPolicy.MAX_FILE_SIZE = 10 * 1024 * 1024`가 같은 한도를 이중으로 들고 있어, 한쪽만 바꾸면 다른 쪽이 조용히 우선한다.

**제안**

- 업로드 대상(`relatedType`)별 최대 크기와 최대 해상도를 서버 정책으로 승격하고, 프론트 정책은 그 값을 화면에 반영하는 역할만 남긴다.
- 대상별 정책을 응답 가능한 형태로 노출하면(`/config` 계열) 프론트가 상수를 복제하지 않아도 된다.
- multipart 한도와 정책 한도의 관계를 주석이나 상수 참조로 명시한다.

### A6. 응답 정규화 shim이 6곳에 흩어져 있다

**현상**

wire 형태와 프론트 내부 타입의 차이를 메우는 `Wire 타입 + normalize()` 쌍이 독립적으로 6벌 존재한다.

- `api/postContract.ts` — `PostSummaryWire`, `PostDetailWire`, `BoardDetailWire`
- `api/feed.ts` — `PersonalFeedItemWire`
- `api/adminAccountApi.ts` — `BoardAdminInfoWire`, `SuperAdminInfoWire`
- `api/notification.ts` — `NotificationRaw`
- `api/emoticon.ts` — `EmoticonMasterWire`
- `utils/pageResponse.ts` — `PageResponseRaw`

여기에 `stores/auth.ts:54`는 패턴을 따르지 않고 인라인으로 처리한다(`userData.isEmailVerified ?? userData.emailVerified ?? false`).

**영향**

같은 종류의 문제를 여섯 번 서로 다르게 풀고 있으며, 새 필드가 추가될 때 어느 파일을 손봐야 하는지 규칙이 없다. 백엔드 DTO를 수정해도 프론트 타입은 아무 신호를 주지 않는다.

**제안**

wire 형태가 A1로 고정되고 나면, springdoc이 이미 제공하는 `/api-docs`에서 TS 타입을 생성하는 경로가 열린다. 생성 타입을 도입하면 손으로 유지하는 응답 타입이 사라지고 normalizer만 의도적인 수기 계층으로 남는다. 도입 전이라도 `stores/auth.ts`의 인라인 처리는 normalizer로 옮겨 패턴을 일치시킨다.

### A7. `PageResponseRaw`에 실패 모드가 없다

**현상**

`utils/pageResponse.ts`의 `PageResponseRaw`는 모든 필드가 optional이고, `normalizePageResponse`가 `?? 0`, `?? 1`, `content.length` 등으로 빈틈을 메운다.

**영향**

백엔드가 `page`를 다른 이름으로 바꾸면 예외 없이 0페이지가 표시된다. 현재는 `ApiWireContractSerializationTest`가 필드 이름을 고정하고 있어 실제 위험은 낮지만, shim 자체는 어긋남을 감지하지 못한다.

**제안**

정규화 함수에 개발 모드 경고를 추가해 `page`와 `number`가 모두 없을 때 로깅한다. 계약 테스트가 1차 방어선이고, 이 경고는 테스트가 놓친 경우의 2차 신호다.

## B. 백엔드 단독

### B1. `validationErrorResponse`의 파라미터 2개가 미사용이다

`GlobalExceptionHandler.java:261`의 `validationErrorResponse(String errorType, HttpServletRequest request)`는 두 파라미터를 본문에서 전혀 참조하지 않는다. 호출부 5곳이 예외 타입 문자열을 넘기지만 어디에도 도달하지 않으며, 원래 `saveErrorLog(...)`에 전달하려던 흔적으로 보인다.

같은 파일의 다른 핸들러(`METHOD_NOT_ALLOWED`, `FILE_TOO_LARGE`, `LOGIN_FAILED`, `FORBIDDEN` 등)는 모두 `saveErrorLog`를 호출하지만 검증 계열 핸들러는 호출하지 않는다. 4xx 검증 오류는 유입량이 많아 error log를 오염시킬 수 있으므로 **의도된 제외일 가능성이 높다.**

**제안**: 의도를 확인한 뒤 둘 중 하나로 정리한다. 로깅이 필요하면 파라미터를 실제로 사용하고, 불필요하면 파라미터를 제거해 의도를 코드로 드러낸다. 현재 상태는 "빠뜨린 것"과 "일부러 뺀 것"이 구분되지 않는다.

### B2. `MessageSource`가 시스템 로케일로 fallback한다

`MessageConfig`의 `ReloadableResourceBundleMessageSource`에 `setFallbackToSystemLocale(false)`와 `setDefaultLocale`이 설정되어 있지 않다.

현재 `messages.properties`와 `messages_en.properties`는 키 150개로 완전히 일치하며 `GlobalExceptionTest`가 parity를 강제하고 있어 **실제 문제는 발생하지 않는다.** 다만 향후 영어 키가 하나 누락되면 Spring은 기본 번들이 아니라 JVM 기본 로케일 번들로 떨어지므로, 한국 로케일 서버에서 영어 사용자가 한국어 메시지를 받게 된다.

**제안**: `setFallbackToSystemLocale(false)`와 `setDefaultLocale(Locale.KOREAN)`을 명시한다. 한 줄 예방 조치이며 현재 동작을 바꾸지 않는다.

## C. 프론트엔드 단독

### C1. 백엔드 에러 코드 리터럴이 6곳에 분산되어 있다

백엔드 `ErrorCode` enum의 코드가 프론트 6개 파일에 각각 로컬 상수 또는 인라인 리터럴로 존재한다.

| 코드 | 위치 |
| --- | --- |
| `U009` | `api/message.ts` |
| `EM006` | `features/emoticon/form/useEmoticonSubmitGuard.ts` |
| `P004` | `features/board/posts/draft/postDraftRecovery.ts` |
| `C006` | `features/user/messages/useMailboxResource.ts` |
| `A009` | `utils/authRedirect.ts` |
| `C012` | `utils/errorHandler.ts:143` (인라인) |

**영향**: 백엔드에서 코드를 바꾸거나 제거해도 프론트는 아무 신호를 주지 않는다. 어떤 코드가 프론트에서 의미를 갖는지 한눈에 파악할 수단도 없다.

**제안**: 공유 상수 모듈로 모아 grep 가능한 단일 지점을 만든다. A1의 리플렉션 테스트와 같은 방식으로, 프론트가 참조하는 코드 집합이 백엔드 enum에 실재하는지 검증하는 단계까지 확장할 수 있다.

## D. 배포

### D1. HSTS `max-age`가 1일이다

`deploy/nginx/security-headers.conf`의 `Strict-Transport-Security "max-age=86400"`은 1일이다. 그 외 보안 헤더(CSP, `X-Frame-Options: DENY`, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`)는 견고하게 구성되어 있다.

**영향**: 1일 이상 재방문하지 않은 사용자는 보호 창이 만료되어 첫 요청이 downgrade 공격에 노출된다.

**제안**: 단계적으로 상향한다(1일 → 7일 → 6개월 → 1년). 하위 도메인 구성을 확인한 뒤 `includeSubDomains` 추가를 검토하고, `preload`는 되돌리기 비용이 크므로 별도 판단한다.

## E. 실행 환경과 시간

### E1. 스케줄러 timezone 지정이 일관되지 않다

**현상**

`@Scheduled` cron 20개 중 일부만 `zone = "Asia/Seoul"`을 지정한다. 지정하지 않으면 JVM 기본 timezone이 적용된다.

zone을 지정한 일별 작업 (의도대로 KST 새벽에 실행):

| 스케줄러 | cron | 실행 시각 |
| --- | --- | --- |
| `PostDraftCleanupScheduler` | `0 15 3 * * ?` | 03:15 KST |
| `TagCleanupScheduler` | `0 20 3 * * ?` | 03:20 KST |
| `UserFeedCleanupScheduler` | `0 30 3 * * ?` | 03:30 KST |
| `ErrorLogCleanupScheduler` | `0 10 4 * * ?` | 04:10 KST |
| `VerificationCodeCleanupScheduler` | `0 20 4 * * ?` | 04:20 KST |
| `RefreshTokenCleanupScheduler` | `0 40 4 * * ?` | 04:40 KST |

zone을 지정하지 않은 일별 작업:

| 스케줄러 | cron | JVM이 UTC일 때 실행 시각 |
| --- | --- | --- |
| `FileCleanupScheduler` | `0 0 2 * * ?` | **11:00 KST** |
| `LoginHistoryCleanupScheduler` | `0 40 3 * * ?` | **12:40 KST** |

시간별·분별 작업(`0 0 * * * ?`, `0 * * * * ?` 등)은 timezone과 무관하게 동작하므로 영향이 없다.

**JVM timezone 확인 결과**: `backend/Dockerfile`(`eclipse-temurin:21-jre-alpine`), `deploy/systemd/app.service`, `docker-compose.yml`, `application*.yml` 어디에도 `TZ`나 `spring.jackson.time-zone` 설정이 없다. Alpine 기본값은 UTC다. systemd 유닛이 읽는 `/etc/noviis/app.env`는 저장소에 없어 확인할 수 없으므로, 실제 값은 운영에서 검증이 필요하다.

**영향**

6개 스케줄러에 명시된 "KST 새벽 유지보수" 의도가 2개 일별 작업에서는 지켜지지 않는다. JVM이 UTC라면 임시 파일 정리와 로그인 이력 정리가 한국 기준 정오 무렵, 즉 트래픽 피크에 배치 삭제를 수행한다.

`deploy/monitoring/scheduled-jobs.txt`와 `verify-scheduled-jobs.py`는 작업의 존재 여부와 최종 실행 경과 시간만 검증하고 실행 시각대는 다루지 않으므로 이 어긋남을 잡지 못한다.

**제안**

- 두 스케줄러에 `zone = "Asia/Seoul"`을 추가한다. **`TZ` 환경변수로 JVM 전체를 KST로 바꾸는 방식은 택하지 않는다** — E2의 타임스탬프 해석이 JVM timezone에 묶여 있어 모든 표시 시각이 9시간 이동한다.
- 일별 cron에 `zone` 지정을 강제하는 규칙을 `verify-scheduled-jobs.py`에 추가해 회귀를 막는다. 매니페스트에 `daily`로 분류된 작업만 검사하면 시간별 작업의 불필요한 잡음을 피할 수 있다.

### E2. 타임스탬프 계약이 timezone-naive하다

**현상**

사용자 대면 도메인의 DTO 62개가 `LocalDateTime`을 쓴다. `BaseTimeEntity`의 `createdAt`·`modifiedAt`도 `LocalDateTime`이고, DB 컬럼은 `timestamp(6)`(timezone 없음)이다. Jackson은 이를 offset 없는 문자열(`"2026-07-25T10:00:00"`)로 직렬화한다.

프론트 `utils/date.ts`의 `toDate`는 두 갈래로 해석한다.

| 입력 형태 | 처리 | 해석 |
| --- | --- | --- |
| `number[]` | `Date.UTC(...)` | **UTC로 간주** |
| `string` | `new Date(str)` | offset이 없으면 ECMAScript 규격상 **브라우저 로컬로 간주** |

두 분기가 서로 다른 기준을 쓴다. 그리고 `formatDate`의 주석은 `"2023-10-27T10:00:00" -> "2023. 10. 27. 10:00:00"`이라고 적혀 있는데, 이는 브라우저 timezone이 서버 timezone과 같을 때만 성립한다.

**영향**

표시 시각의 정확성이 "JVM timezone == 뷰어의 브라우저 timezone"이라는, 어디에도 고정되어 있지 않은 전제에 의존한다. 어긋나면 다음이 함께 틀어진다.

- `formatTimeAgo`는 서버 시각과 브라우저 `new Date()`를 직접 뺀다. 방금 작성한 글이 "9시간 전"으로 표시되거나 상대 시간 구간을 건너뛰어 절대 날짜로 떨어진다.
- `postDraftRecovery.pickNewestDraftSnapshot`은 브라우저가 생성한 로컬 스냅샷 시각(`toISOString()`, 실제 UTC)과 서버 시각(로컬로 해석됨)을 문자열 비교해 최신본을 고른다. 계통적 편차가 있으면 항상 한쪽만 승리하므로, 임시저장 복구가 서버 최신본 또는 로컬 최신본을 일관되게 버린다.
- 해외 사용자는 서버 timezone과 무관하게 항상 어긋난 시각을 본다.

**E3과의 관계**: 이 항목은 "브라우저가 서버 시각을 어떻게 읽는가"의 문제다. 서버가 **어떤 기준으로 시각을 쓰는가**는 별개이며 더 심각하다. E3을 먼저 읽어야 이 절의 전제가 성립한다.

**제안**

wire 계약 변경이므로 단계를 나눈다.

1. **확인**: 운영 JVM의 실제 timezone과 DB에 저장된 시각의 기준을 먼저 확정한다. 이 값 없이는 이후 판단이 불가능하다.
2. **응급**: `toDate`의 문자열 분기를 배열 분기와 같은 기준으로 통일하고, 어느 쪽을 정본으로 삼을지 한 곳에 문서화한다. 두 분기가 다른 해석을 쓰는 상태 자체가 결함이다.
3. **정본화**: 신규 응답 필드부터 `Instant`(직렬화 시 `Z` 접미사 포함)로 전환하고, 기존 필드는 A1과 같은 방식으로 legacy 허용 목록에 등재해 점진 이행한다. 프론트는 offset이 있는 문자열을 그대로 `new Date()`에 넘기면 되므로 정규화 계층이 필요 없다.
4. 전환 전까지 `LocalDateTime` 필드가 늘어나지 않도록 A1의 DTO 스캔 테스트에 검사를 함께 넣는다.

### E3. 백엔드 내부에 시간 기준 두 개가 공존한다

**현상**

저장되는 `LocalDateTime` 값이 두 가지 서로 다른 기준으로 생성된다.

**기준 1 — KST.** `TimeConfig`가 `Clock.system(DateTimeUtils.KST_ZONE_ID)`(`Asia/Seoul`)를 빈으로 등록하고, 15개 이상의 서비스가 이를 주입받아 `LocalDateTime.now(clock)`으로 시각을 만든다. `AttendanceService`, `PostListReadService`, `ScheduledPostService`, `PollService`, `NotificationDeliveryJobProcessor`, `PushDeliveryJobProcessor` 등이 여기 속한다.

**기준 2 — JVM 기본 timezone.** 다음 지점은 주입 clock을 쓰지 않고 맨 `LocalDateTime.now()`를 호출한다.

| 위치 | 용도 |
| --- | --- |
| `BaseTimeEntity`의 `@CreatedDate`·`@LastModifiedDate` | **모든 엔티티의 `created_at`·`modified_at`** |
| `NotificationDeliveryJobTransaction.java:51, 70, 85` | 알림 작업 실패·거부 시각 |
| `UserSettingsService.java:87` | 온보딩 완료 시각 |
| `ModerationAuditLog.java:88` | 감사 로그 생성 시각 |

`WhiteboardApplication.java:9`의 `@EnableJpaAuditing`에는 `dateTimeProviderRef`가 지정되어 있지 않고, 커스텀 `DateTimeProvider` 빈도 없다. 따라서 Spring Data는 기본 `CurrentDateTimeProvider`를 쓰며 이는 JVM 기본 timezone을 따른다. **컨텍스트에 `Clock` 빈이 있어도 auditing은 이를 사용하지 않는다.**

E1에서 확인했듯 JVM timezone은 어디에도 고정되어 있지 않고 Alpine 기본값은 UTC다.

**확인된 파급 — 인기글 기간 필터**

두 기준이 같은 쿼리에서 만나는 경로를 끝까지 추적했다.

```
PostListReadService.resolveTrendingSince()
  → now() = LocalDateTime.now(clock)          // 기준 1: KST
  → postRepository.findTrendingPosts(since, …)
  → PostRepositoryCustomImpl:570  post.createdAt.goe(since)
      post.createdAt = BaseTimeEntity @CreatedDate  // 기준 2: JVM 기본
```

JVM이 UTC라면 `now(KST)`가 저장값보다 9시간 앞서므로, `now(KST).minusHours(24)`는 실질적으로 `now(UTC).minusHours(15)`가 된다.

| 요청 기간 | 실제 적용 구간 |
| --- | --- |
| `24h` | 최근 15시간 |
| `7d` | 최근 6일 15시간 |
| `30d` | 최근 29일 15시간 |

`PostRepository.java:171-175`의 오늘·어제 버킷 분류(`:todayStart`, `:tomorrowStart`, `:yesterdayStart`)도 같은 방식으로 어긋난다.

**확인된 파급 — 알림 재시도 스케줄**

`NotificationDeliveryJobTransaction.fail(...)`은 `claimedAt`과 `nextAttemptAt`을 호출자(`NotificationDeliveryJobProcessor`, 기준 1)로부터 받으면서, 실패 시각은 자체적으로 `LocalDateTime.now()`(기준 2)로 만든다. 한 행 안에 9시간 어긋난 두 시각이 함께 기록되며 재시도·dead-letter 판정이 혼합된 기준을 비교한다.

**전제 확인 필요**

systemd 유닛이 읽는 `/etc/noviis/app.env`는 저장소에 없어 실제 `TZ` 값을 확인할 수 없다. 여기에 `TZ=Asia/Seoul`이 설정되어 있다면 두 기준이 우연히 일치해 현재는 증상이 나타나지 않는다. **그 경우에도 결함은 남는다** — 두 기준이 독립적으로 정의되어 있어 우연에 의해서만 맞고, 이 파일은 저장소 리뷰 대상이 아니며, E1을 고치려고 `TZ`를 건드리는 순간 조용히 깨진다.

**제안**

1. **즉시 확인**: 운영 JVM의 `user.timezone`과 `created_at` 표본값의 기준을 확인한다. 이 값이 이후 모든 판단의 전제다.
2. **기준 통일**: `@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")`와 함께 주입 `Clock`을 사용하는 `DateTimeProvider` 빈을 등록해 auditing을 기준 1로 맞춘다. 이것만으로 가장 넓은 표면(모든 엔티티의 `created_at`)이 정리된다.
3. **잔여 지점 정리**: 위 표의 나머지 3개 지점을 주입 `Clock` 사용으로 바꾼다.
4. **회귀 방지**: 맨 `LocalDateTime.now()`·`LocalDate.now()` 호출을 금지하는 정적 검사를 추가한다. ArchUnit 또는 기존 `deploy/monitoring`의 파이썬 검증 스크립트 방식 어느 쪽이든 가능하다.
5. **데이터 보정**: 1번 확인 결과 기존 데이터에 두 기준이 섞여 있다면, 보정 범위와 방법을 별도 마이그레이션으로 다룬다. 코드만 고치면 과거 행은 어긋난 채 남는다.

2~3번은 저장 시각의 의미를 바꾸므로 5번과 함께 계획해야 한다. 순서를 뒤집으면 신·구 데이터가 섞인다.

## F. 내부 API 설계

### F1. `PageRequestUtils` 오버로드의 두 번째 인자 의미가 충돌한다

**현상**

`PageRequestUtils`는 `of`·`bounded` 오버로드를 9개 제공하는데, 같은 위치의 `int` 인자가 오버로드마다 다른 뜻을 가진다.

| 시그니처 | 두 번째 `int`의 의미 |
| --- | --- |
| `bounded(Pageable, int defaultPageSize, int maxPageSize)` | 기본 크기 |
| `bounded(Pageable, int maxPageSize, Sort, Set)` | **최대 크기** (기본값으로도 함께 쓰임) |

**범위 주의**: `bounded(...)` 호출부는 저장소 전체에 5곳뿐이며 **전부 agent 도메인**(`AgentNoteService`, `AgentQueryService`)이다. 이번 검토 범위에는 호출부가 하나도 없다. 반면 `PageRequestUtils` 자체는 `global/common/util`에 있는 공용 클래스이고, 범위 내 코드는 `of(...)` 계열을 68곳에서 사용한다.

또한 잘못된 입력의 처리 방식도 계열마다 다르다. `of(...)`는 `size < 1`에 `VALIDATION_ERROR`를 던지고, `bounded(...)`는 `Math.max(requestedSize, 1)`로 조용히 보정한다.

**영향**

현재 범위 내에서 실제로 잘못 호출되고 있는 곳은 없다. 다만 공용 유틸리티에 남은 잠재적 함정이다. 범위 내 도메인이 향후 `bounded(...)`를 쓰기 시작하면 두 번째 인자가 기본값인지 상한인지 호출부만으로 판별되지 않고, 타입이 같으므로 컴파일러도 테스트도 잡지 못한다. `of`와 `bounded`의 입력 검증 방식이 다른 점은 지금도 공용 유틸리티의 일관성 문제다.

우선순위가 낮은 이유가 여기에 있다. **범위 내 실사용 결함이 아니라 공용 코드의 설계 정리 항목이다.**

**제안**

- 최소 조치로 `bounded(Pageable, int, Sort, Set)` 오버로드의 파라미터 이름을 호출 의미에 맞게 정리하고 Javadoc으로 상한·기본값 관계를 명시한다.
- 근본 조치로는 `PageBounds`류의 명시적 값 객체(기본 크기와 상한을 필드로 분리)를 받아 오버로드 수를 줄인다.
- `of`와 `bounded`의 잘못된 입력 처리(예외 대 보정)를 한쪽으로 통일한다.

### F2. 익명 캐시가 in-process라 scale-out의 선결 과제다

**현상**

`CacheConfig`의 캐시 5종은 모두 Caffeine 로컬 캐시다. 익명 사용자 대상 캐시(`boardCatalogAnonymous` 60초, `boardDetailAnonymous`·`trendingPostsAnonymous`·`homeLandingAnonymous` 각 30초)는 JVM 프로세스 안에만 존재한다.

현재 배포는 systemd `Type=simple` 단일 인스턴스(`deploy/systemd/app.service`)이고 compose에도 replica 설정이 없으므로 **지금은 문제가 되지 않는다.**

**영향**

인스턴스를 늘리는 순간 익명 응답이 인스턴스별로 최대 30~60초 어긋나고, `AnonymousReadCacheInvalidator`의 무효화가 자신의 프로세스에만 적용되어 게시글 수정이 일부 사용자에게 지연 반영된다.

**제안**

수평 확장 계획이 생길 때 착수할 항목으로 기록해 둔다. 그 시점에 공유 캐시로 전환하거나, 무효화 이벤트를 인스턴스 간에 전파하는 방식을 선택한다. 현재 토폴로지에서는 조치가 필요 없다.

## 검토 결과 양호한 영역

기록 목적으로 남긴다. 아래 항목은 점검했고 조치가 필요하지 않다.

- **N+1 방어**: `@EntityGraph` 103곳, fetch join 34곳. 서비스 계층은 `findAllById`, `countByBoardIds`, `findByPostIdIn` 등 batch 조회로 일관되며 반복문 내 단건 조회 패턴을 발견하지 못했다.
- **트랜잭션 경계**: 파사드에 `@Transactional`을 두고 package-private 협력 서비스를 호출하는 패턴이 일관된다. `BoardApplicationService`가 비트랜잭션인 것은 쓰기 커밋 후 재조회를 위한 의도적 구성으로 보인다.
- **업로드 보안 검증**: magic byte 판별, 선언 MIME·확장자 교차 검증, 파일명 정규화, 해상도·픽셀 상한 검사가 모두 갖춰져 있다. A5는 보안이 아니라 대상별 정책 문제다.
- **커버리지 게이트**: 백엔드 `jacocoTestCoverageVerification`(기본 규칙 50%, LINE 75% / BRANCH 55%, 지정 서비스 11개는 LINE 80%)과 프론트 vitest thresholds(statements 75 / branches 65 / functions 70 / lines 75)가 모두 CI에서 강제된다.
- **접근성**: `@axe-core/playwright` 기반 e2e가 로그인·홈·게시글 상세·설정·에디터·다이얼로그 포커스 트랩까지 덮으며 CI에서 실행된다.
- **보안 헤더**: CSP가 `default-src 'self'`, `object-src 'none'`, `frame-ancestors 'none'`으로 조여져 있고 report-uri까지 연결되어 있다.
- **번들 분할**: 라우터 52개 항목이 전부 동적 import이며 뷰 정적 import이 없다.
- **타입 안정성**: `any` 3건, `@ts-ignore` 0건, `eslint-disable` 0건, TODO/FIXME 0건.
- **메시지 번들**: 한국어·영어 각 150키로 완전 일치하며 `GlobalExceptionTest`가 parity와 `MessageFormat` 렌더링을 검증한다.
- **CI 구성**: paths-filter로 검증 범위와 배포 범위를 분리하고, 배포 필터만 테스트 파일을 제외한다. 검증 필터는 `frontend/**` 전체를 포함하므로 테스트 변경도 CI를 거친다. Actions는 SHA로 고정되어 있다.
- **페이지네이션 방어**: `PageRequestUtils`가 페이지 크기를 상한으로 클램프하고, 정렬 속성을 allowlist로 걸러 정렬 주입을 차단한다. 설계 정리 여지는 F1로 분리했고 방어 자체는 유효하다.
- **로그 마스킹**: `SensitiveDataMaskingFilter`가 AOP 파라미터 로깅, logback 패턴 변환(`MaskedMessageConverter`), DB 에러 로그 적재 세 경로에 모두 적용된다.
- **i18n 강제**: 커스텀 ESLint 규칙 `local-i18n/no-bare-korean-in-template`이 error 수준으로 걸려 있고, `messages.spec.ts`·`keyUsage.spec.ts`가 키 존재와 사용 여부를 검증한다.
- **비동기 실행**: 용도별 executor 3종(durable·notification·observability)을 분리하고 거부 정책을 구분했으며, MDC 전파와 큐 게이지 메트릭을 갖췄다. 미처리 예외 핸들러도 등록되어 있다.
- **DB 마이그레이션**: Flyway 88개 파일이 버전 순으로 관리되고, 계약성 마이그레이션은 `docs/ops/applied-contract-migrations.txt`로 별도 추적한다. `open-in-view: false`로 뷰 계층 지연 로딩도 차단되어 있다.

## 진행 제안

의존 관계를 고려한 순서다.

0. **E3의 1단계(운영 JVM timezone과 `created_at` 표본 확인)** — 조사만으로 끝나며 비용이 거의 없다. E1·E2·E3의 실제 심각도가 모두 이 결과에 달려 있으므로 가장 먼저 수행한다.
1. **E3의 2~3단계(시간 기준 통일)** — 현재 동작에 영향이 확인된 유일한 항목이다. 5단계(데이터 보정)와 함께 계획한다.
2. **E1** — 스케줄러 2개에 `zone`을 추가하는 국소 변경이다. E3 확인 결과와 무관하게 안전하며, `TZ` 방식을 배제하는 판단만 지키면 된다.
3. **A2** — 이미 구현·검증된 서버 기능을 클라이언트에 연결하는 것으로, 변경 범위가 좁고 효과가 즉시 나타난다.
4. **E2의 2단계(`toDate` 분기 통일)** — E3의 기준이 확정된 뒤에 착수한다. 그전에 손대면 잘못된 기준으로 고정된다.
5. **A1** — 이후 모든 DTO 변경의 재발을 막는 구조적 방어이며, A6의 선행 조건이다. E3 4단계의 정적 검사와 함께 설계하면 작업이 겹치지 않는다.
6. **A4**, **A3** — 사용자에게 보이는 오류 표시와 실시간 채널의 신뢰도를 올린다.
7. **A5** — 서버 정책 설계가 필요하므로 별도 논의를 거친다.
8. **B1**, **B2**, **C1**, **D1** — 국소 변경이며 위 항목과 독립적으로 처리 가능하다.
9. **A6**, **A7** — A1 완료 후 착수한다.
10. **F1**, **F2** — 범위 내 실사용 결함이 아니다. 공용 유틸리티 정리나 수평 확장 계획이 생길 때 착수한다.
