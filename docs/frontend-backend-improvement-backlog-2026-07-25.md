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
| B2 | `spring.messages` 설정이 죽어 있고 로케일 fallback 동작이 미확인이다 | 백엔드 | 하 |
| C1 | 백엔드 에러 코드 리터럴이 6곳에 분산되어 있다 | 프론트엔드 | 하 |
| D1 | HSTS `max-age`가 1일이다 | 배포 | 하 |
| E1 | 스케줄러 timezone 지정이 일관되지 않다 | 실행 환경 | 하 |
| E2 | wire 타임스탬프에 offset이 없어 KST 밖 사용자에게 어긋난다 | 실행 환경 | 상 |
| E3 | 시간 기준 통일이 `setDefault` 한 줄에 의존한다 | 실행 환경 | 하 |
| F1 | `PageRequestUtils` 오버로드의 두 번째 인자 의미가 충돌한다 | 내부 API | 하 |
| F2 | 익명 캐시가 in-process라 scale-out의 선결 과제다 | 실행 환경 | 하 |
| **G1** | **`UserSettings.timezone`이 저장만 되고 쓰이지 않는다** | **기능** | **중** |
| **G2** | **시각 표시를 사용자 지역 기준으로 전환하는 설계** | **설계** | — |
| **A8** | **51개 boolean 필드가 wire에 키를 두 개씩 내보낸다** | **계약** | **중** |

### 2026-07-25 정정

초판은 JVM timezone이 UTC일 가능성을 전제로 E1을 "중", E3을 "최상"으로 기재하고 인기글 기간 필터가 실제로 어긋난다고 적었다. **이 판단은 틀렸다.**

- `WhiteboardApplication.java:15`의 `@PostConstruct`가 기동 시 `TimeZone.setDefault("Asia/Seoul")`을 호출해 JVM 기본 timezone을 KST로 강제한다. 초판 조사는 Dockerfile·systemd 유닛·compose·`application*.yml`만 확인하고 이 지점을 놓쳤다.
- 운영 timezone이 KST임을 확인했다.

따라서 주입 `Clock`(KST)과 JPA auditing(JVM 기본 = KST)은 **같은 기준**이며, 인기글 기간 필터와 알림 재시도 스케줄에 현재 어긋남은 없다. E1·E3은 실동작 결함이 아니라 명시성 항목으로 내리고, 해당 파급 서술을 삭제했다.

### 2026-07-25 전면 재검증

정정 이후 문서의 모든 항목을 근거까지 다시 확인했다. 결과는 다음과 같다.

- **근거 유지 (재확인 완료)**: A1, A2, A3, A4, A5, A6, A7, B1, C1, D1, E2, F1, F2, G1
  - A3은 백엔드 전체 `SseEmitter.event()` 호출 6곳을 전수 확인했다. 발신 이름은 `connect`·`notification`·`comment`·`comment-topic-invalidated`·`comment-topic-access-revoked`와 heartbeat 주석뿐이며, `message`는 없다.
  - A2는 프론트 전체에서 `Retry-After`를 읽는 코드가 없음을 재확인했다. `apiRefreshRetry.ts`의 `retryAfterRefresh`는 이름만 유사한 인증 토큰 갱신 로직으로 무관하다.
- **수정**: B2 — 초판이 프레임워크 기본값을 단정했으나 이 환경에서 검증할 수 없었다. 대신 검증 가능한 사실(`spring.messages` 블록이 커스텀 빈에 의해 무효화됨)을 근거로 교체했다.
- **기각**: 날짜 전용(`LocalDate`) 필드의 하루 밀림을 의심해 확인했으나 **결함이 아니었다.** `MyAttendance.vue`는 달력 셀을 문자열로 만들어 문자열끼리 비교하고, 라벨 렌더링에서만 `T00:00:00`을 명시적으로 덧붙여 로컬 기준으로 파싱한다. 의도적으로 올바른 처리이므로 G2 진행 시 이 패턴을 깨지 않아야 한다.

**E2는 정정 대상이 아니다.** 오히려 이 확인으로 성격이 분명해졌다 — 서버는 KST 벽시계 값을 offset 없이 내보내므로, 브라우저가 KST인 사용자에게만 우연히 맞고 그 밖의 사용자에게는 항상 어긋난다. G1은 이 문제를 다루기 위해 필요한 설계를 정리한 신규 항목이다.

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
2. 필드에 `@JsonProperty` — **두 이름이 함께 나간다** (`PostResponse.isNotice`). 초판은 이를 "`is` 유지"로 잘못 적었다. A8 참고.
3. getter에 `@JsonProperty` — `is` 유지 (`LoginResponse.java:27`)

`PostSummary` 한 클래스 안에서도 `isSpoiler`·`isSecret`·`isBlinded`는 `is`를 유지하고 `isNotice`·`isNsfw`·`isLiked`·`isScrapped`·`isSubscribed`는 떨어진다.

**영향**

지금 깨진 화면은 없다. 이 항목의 값어치는 현재 결함이 아니라 **재발 방지**에 있다. 새 DTO에 `boolean isXxx` 필드를 추가할 때 어느 규칙을 따를지 코드가 알려주지 않고, 어긋나도 빌드가 통과한다. 프론트가 해당 필드를 읽으면 값은 조용히 `undefined`가 되어 falsy로 동작한다.

**제안**

손으로 나열하는 테스트를 리플렉션 기반 스캔으로 교체한다. `*/dto/*` 하위 클래스를 순회하며 `boolean is[A-Z]*` 필드를 전부 수집하고, 각 필드가 다음 중 하나를 만족하지 못하면 실패시킨다.

- **getter**에 `@JsonProperty`가 붙어 있다
- 명시적 legacy 허용 목록에 등재되어 있다

**구현 중 정정**: 초판은 "필드 또는 getter"라고 적었으나, 필드 어노테이션은 이름을 바꾸지
못하고 키를 하나 더 만든다. 상세와 파급은 A8에 기록했다. 따라서 규칙은 getter 어노테이션만
인정한다.

허용 목록은 어노테이션이 없는 10개 필드와, 필드 어노테이션으로 중복 키를 내보내는 51개
필드로 나누어 시작한다. 스캔 대상에서 agent·ad를 제외할지는 두 도메인 소유 주체와 협의해 정한다. 이렇게 하면 신규 DTO는 규칙을 따르거나 목록에 의식적으로 등재하는 것 외의 선택지가 없어진다. 이후 `API명세서.md`의 표는 허용 목록에서 생성하거나, 최소한 목록과 표의 일치를 같은 테스트에서 검증한다.

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
- **`message`** — `notificationStreamController.ts:124`에서 `notification`과 동일하게 처리한다.

**`message` 관련 정정 (구현 중 확인)**

초판은 이 분기를 "제거된 기능의 잔재"로 추정했다. **이 추정은 틀렸다.** `message`는 SSE 규격이 정한, `event:` 줄이 없는 프레임의 기본 이름이며 `notificationSseStream.ts:29,35,44,71`이 파서에서 이 값을 채운다. 백엔드가 보내는 이름이 아니라 프로토콜 기본값이므로 제거하면 이름 없는 data 프레임이 조용히 버려진다. 분기는 유지하고 그 이유를 주석으로 남겼다.

**영향**

이름 하나를 백엔드에서 바꿔도 프론트 빌드·테스트가 아무 신호를 주지 않는다. REST envelope에는 직렬화 계약 테스트가 있지만 SSE 채널에는 대응 장치가 없다.

**제안**

- 이벤트 이름을 백엔드 상수 클래스로 모으고, 상수를 거치지 않은 문자열 리터럴 사용을 원본 검사로 막는다.
- 프론트는 이름 집합을 union 타입으로 선언하고, 백엔드 상수 파일을 읽어 두 집합이 일치하는지 검증한다.
- SSE 프로토콜 기본값(`message`)은 백엔드 이벤트 집합과 분리해 표기한다.

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

### A5 구현 노트 (2026-07-25)

업로드 시점의 `target`은 클라이언트가 보내는 값이라, 생략하면 가장 관대한 `GENERIC`으로
떨어진다. 즉 그것만으로는 "UI를 거치지 않는 호출"을 막지 못하고 UI 경로에만 강제가 걸린다.

그래서 두 겹으로 나눴다.

| 시점 | 기준 | 위조 가능 | 역할 |
| --- | --- | --- | --- |
| 업로드 | 클라이언트가 보낸 `target` | 가능 | 사용자에게 빨리 알려 주는 fail-fast |
| 연결 | 엔드포인트가 정하는 `relatedType` | 불가 | 실제 강제 |

연결 시점 검사는 `FileAssociationService.associateLoadedFileIfAllowed`에 두었다. 이미 연결된
파일은 호출부에서 걸러지므로 과거 정책으로 저장된 파일은 영향을 받지 않는다.

해상도 위반에는 `F008 FILE_DIMENSION_TOO_LARGE`를 새로 두었다. 기존에는 형식 오류와 같은
`INVALID_FILE_TYPE`을 써서 클라이언트가 "이미지가 아님"과 "해상도가 큼"을 구분할 수 없었다.

프론트 상한과 서버 상한을 비교할 때는 대상이 원본을 그대로 올리는지 따져야 한다.
프로필 이미지와 이모티콘은 소스 파일을 검사한 뒤 축소해서 올리므로, 소스 크기 제한은
업로드 크기의 상한이 아니다. 비교해야 할 값은 축소 목표 해상도다.

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

### A8. 51개 boolean 필드가 wire에 키를 두 개씩 내보낸다

**A1 구현 중 발견한 신규 항목이다.** 초판 A1은 "필드에 `@JsonProperty`를 붙이면 wire 이름이
명시된다"를 전제로 삼았다. **이 전제는 틀렸다.**

**현상**

`boolean isXxx` 필드의 wire 이름은 어노테이션 위치로 갈린다. 실제 직렬화로 확인했다.

| 패턴 | wire 이름 |
| --- | --- |
| 어노테이션 없음 | `xxx` 하나 |
| **필드**에 `@JsonProperty` | `xxx`와 `isXxx` **둘 다** |
| **getter**에 `@JsonProperty` | `isXxx` 하나 |

필드에 붙이면 getter에서 파생된 `xxx` 속성이 그대로 남고 필드가 **별도 속성으로 추가**된다.
이름이 바뀌는 것이 아니라 키가 늘어난다.

실제 응답 예시다.

```
PostSummary  → ..., blinded, isBlinded, isSecret, isSpoiler, ..., secret, spoiler, ...
PostResponse → ..., blinded, isBlinded, isLiked, isNotice, isNsfw, isScrapped, isSecret,
                isSpoiler, ..., liked, notice, nsfw, scrapped, secret, spoiler, ...
```

`PostResponse`는 7쌍이 중복이다. 저장소 전체에서 **51개 필드**가 이 상태다.

**영향**

- 모든 게시글·댓글·스페이스 응답이 boolean 키를 두 배로 싣는다. 목록 응답에서는 항목 수만큼 곱해진다.
- 프론트엔드가 두 이름을 섞어 읽는다(`notice` 118회, `isNotice` 115회). 중복 덕분에 양쪽 다 동작하고 있어 문제가 드러나지 않았다.
- `API명세서.md`의 기존 표는 절반만 맞았다. `isSpoiler`가 유지된다고 적었지만 `spoiler`도 함께 나간다.

**제안**

키 제거는 wire 축소이므로 프론트 정리가 선행되어야 한다.

1. 프론트엔드가 참조하는 이름을 한쪽으로 모은다. 정규화 계층(`postContract.ts`)이 이미 있으므로 그 안에서 흡수할 수 있다.
2. 백엔드를 `@Getter(onMethod_ = @JsonProperty("isXxx"))` 패턴으로 옮긴다. `ScheduledPostDetailResponse`가 이미 이 방식을 쓰고 있어 참고할 선례가 있다.
3. `BooleanWireNameContractTest`의 `LEGACY_DUPLICATE_KEYS`에서 해당 항목을 지운다. 목록이 비면 이 항목이 끝난다.

당장 깨진 것은 없으므로 우선순위는 중이다. 다만 목록이 51개에서 더 늘지 않도록 테스트가 막고 있다.

## B. 백엔드 단독

### B1. `validationErrorResponse`의 파라미터 2개가 미사용이다

`GlobalExceptionHandler.java:261`의 `validationErrorResponse(String errorType, HttpServletRequest request)`는 두 파라미터를 본문에서 전혀 참조하지 않는다. 호출부 5곳이 예외 타입 문자열을 넘기지만 어디에도 도달하지 않으며, 원래 `saveErrorLog(...)`에 전달하려던 흔적으로 보인다.

같은 파일의 다른 핸들러(`METHOD_NOT_ALLOWED`, `FILE_TOO_LARGE`, `LOGIN_FAILED`, `FORBIDDEN` 등)는 모두 `saveErrorLog`를 호출하지만 검증 계열 핸들러는 호출하지 않는다. 4xx 검증 오류는 유입량이 많아 error log를 오염시킬 수 있으므로 **의도된 제외일 가능성이 높다.**

**제안**: 의도를 확인한 뒤 둘 중 하나로 정리한다. 로깅이 필요하면 파라미터를 실제로 사용하고, 불필요하면 파라미터를 제거해 의도를 코드로 드러낸다. 현재 상태는 "빠뜨린 것"과 "일부러 뺀 것"이 구분되지 않는다.

### B2. `spring.messages` 설정이 죽어 있고 로케일 fallback 동작이 미확인이다

**현상 1 — 죽은 설정 (확인됨)**

`application.yml`에 `spring.messages` 블록이 있다.

```yaml
  messages:
    basename: messages
    encoding: UTF-8
```

그런데 `MessageConfig`가 `messageSource`라는 이름의 빈을 직접 정의한다. Spring Boot의 `MessageSourceAutoConfiguration`은 `@ConditionalOnMissingBean(name = "messageSource")` 조건이므로 자동설정 전체가 물러나고, **`spring.messages.*` 값은 하나도 적용되지 않는다.** 현재 두 곳이 같은 값(`messages`, `UTF-8`)을 지정하고 있어 증상은 없지만, 설정 출처가 둘이고 그중 하나는 무효다.

실질적 위험은 이것이다. 아래 현상 2를 고치려고 `spring.messages.fallback-to-system-locale: false`를 추가하면 **조용히 무시된다.** 설정을 넣었는데 동작이 안 바뀌는 형태의 디버깅이 발생한다.

**현상 2 — fallback 동작 (미확인)**

`MessageConfig`의 `ReloadableResourceBundleMessageSource`에 `setFallbackToSystemLocale`과 `setDefaultLocale`이 명시되어 있지 않다.

**이 절의 초판은 프레임워크 기본값이 `true`라고 단정했으나, 이 환경에서는 Spring 의존성 jar를 확인할 수 없어 검증하지 못했다.** Spring Boot 4.1(Spring Framework 7.x) 기준 실제 기본값은 착수 전에 확인이 필요하다.

기본값이 `true`라면, 영어 번들에 키가 누락됐을 때 기본 번들이 아니라 JVM 기본 **로케일**(timezone이 아니다) 번들로 떨어진다. 현재 `messages.properties`와 `messages_en.properties`는 키 150개로 완전히 일치하고 `GlobalExceptionTest`가 parity를 강제하므로 **어느 쪽이든 지금 증상은 없다.**

**제안**

1. `spring.messages` 블록을 제거하거나, 반대로 `MessageConfig`의 커스텀 빈을 없애고 Boot 자동설정에 맡긴다. 후자를 택하면 `spring.messages.fallback-to-system-locale` 같은 속성이 정상 동작한다. 단 `LocalValidatorFactoryBean`이 같은 설정 클래스에 있으므로 함께 정리해야 한다.
2. 현상 2의 실제 기본값을 확인한 뒤, 필요하면 `setFallbackToSystemLocale(false)`와 `setDefaultLocale`을 명시한다. 1번에서 커스텀 빈을 유지하기로 했다면 코드로, 자동설정으로 옮겼다면 속성으로 지정한다.

우선순위는 낮다. 현재 동작에 문제가 없고, parity 테스트가 실질적인 방어선 역할을 하고 있다.

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

`@Scheduled` cron 20개 중 일부만 `zone = "Asia/Seoul"`을 지정한다. 일별 작업 8개 중 6개는 지정하고 2개는 지정하지 않는다.

| 스케줄러 | cron | `zone` |
| --- | --- | --- |
| `PostDraftCleanupScheduler` | `0 15 3 * * ?` | 지정 |
| `TagCleanupScheduler` | `0 20 3 * * ?` | 지정 |
| `UserFeedCleanupScheduler` | `0 30 3 * * ?` | 지정 |
| `ErrorLogCleanupScheduler` | `0 10 4 * * ?` | 지정 |
| `VerificationCodeCleanupScheduler` | `0 20 4 * * ?` | 지정 |
| `RefreshTokenCleanupScheduler` | `0 40 4 * * ?` | 지정 |
| `FileCleanupScheduler` | `0 0 2 * * ?` | **없음** |
| `LoginHistoryCleanupScheduler` | `0 40 3 * * ?` | **없음** |

**영향**

`WhiteboardApplication.java:15`가 JVM 기본 timezone을 KST로 강제하므로 `zone`을 지정하지 않은 두 작업도 결과적으로 02:00 KST, 03:40 KST에 실행된다. **현재 실행 시각은 의도대로다.**

남는 문제는 두 작업의 정확성이 `setDefault` 한 줄에 암묵적으로 의존한다는 점이다. 나머지 6개는 그 줄이 없어도 옳게 동작하지만 이 둘은 아니다.

**제안**

- 두 스케줄러에 `zone = "Asia/Seoul"`을 추가해 나머지와 표기를 맞춘다. 동작 변화는 없고 의존만 제거된다.
- 일별 cron에 `zone` 지정을 요구하는 규칙을 `verify-scheduled-jobs.py`에 추가한다. 매니페스트에서 `daily`로 분류된 작업만 검사하면 시간별 작업의 잡음을 피할 수 있다.

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

### E3. 시간 기준 통일이 `setDefault` 한 줄에 의존한다

**현상**

저장되는 `LocalDateTime` 값이 두 갈래 경로로 생성된다.

**경로 1 — 주입 `Clock`.** `TimeConfig`가 `Clock.system(DateTimeUtils.KST_ZONE_ID)`를 빈으로 등록하고, 15개 이상의 서비스가 이를 주입받아 `LocalDateTime.now(clock)`을 호출한다. `AttendanceService`, `PostListReadService`, `ScheduledPostService`, `PollService`, `NotificationDeliveryJobProcessor` 등이다.

**경로 2 — JVM 기본 timezone.** 다음 지점은 주입 clock 없이 맨 `LocalDateTime.now()`를 호출한다.

| 위치 | 용도 |
| --- | --- |
| `BaseTimeEntity`의 `@CreatedDate`·`@LastModifiedDate` | 모든 엔티티의 `created_at`·`modified_at` |
| `NotificationDeliveryJobTransaction.java:51, 70, 85` | 알림 작업 실패·거부 시각 |
| `UserSettingsService.java:87` | 온보딩 완료 시각 |
| `ModerationAuditLog.java:88` | 감사 로그 생성 시각 |

`@EnableJpaAuditing`에 `dateTimeProviderRef`가 없고 커스텀 `DateTimeProvider` 빈도 없으므로, auditing은 기본 `CurrentDateTimeProvider`를 통해 JVM 기본 timezone을 따른다. **컨텍스트에 `Clock` 빈이 있어도 auditing은 이를 사용하지 않는다.**

**영향**

`WhiteboardApplication.java:15`의 `@PostConstruct`가 JVM 기본을 `Asia/Seoul`로 강제하므로 두 경로는 같은 값을 낸다. **현재 데이터에 어긋남은 없다.**

남는 것은 결합의 형태다. 두 경로는 서로 독립적으로 정의되어 있고, 오직 `setDefault` 한 줄 덕분에 일치한다. 구체적으로 다음이 걸린다.

- 그 줄을 지우거나 `@PostConstruct` 실행 순서가 바뀌면 두 경로가 조용히 갈라진다. 컴파일 오류도 테스트 실패도 나지 않고 데이터만 어긋난다.
- 테스트에서 고정 `Clock`을 주입해도 `created_at`은 실제 시각으로 기록된다. 시간 의존 로직의 재현 테스트를 쓸 때 경로 2를 통제할 수단이 없다.
- `setDefault`는 JVM 전역 상태를 바꾸므로 라이브러리·드라이버의 시각 처리에도 함께 영향을 준다.

**제안**

우선순위는 낮다. 현재 결함이 아니라 구조 정리다.

1. 주입 `Clock`을 사용하는 `DateTimeProvider` 빈을 등록하고 `@EnableJpaAuditing(dateTimeProviderRef = ...)`로 연결한다. auditing이 `setDefault`가 아니라 `Clock`을 따르게 되어 의존이 하나로 모인다.
2. 위 표의 나머지 3개 지점을 주입 `Clock` 사용으로 바꾼다.
3. 맨 `LocalDateTime.now()`·`LocalDate.now()` 호출을 금지하는 정적 검사를 추가한다. 1~2번을 마친 뒤에 켜야 기존 코드가 걸리지 않는다.
4. 1~3번이 끝나면 `setDefault` 호출의 필요성을 재검토한다. G1의 wire 변경까지 마치면 이 줄은 제거 후보가 된다.

**주의**: 이 항목은 저장 시각의 *기준*을 바꾸지 않는다. 1~2번은 같은 값을 다른 경로로 얻게 만들 뿐이므로 데이터 보정이 필요 없다. 기준 자체를 바꾸는 것은 G1의 범위다.

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

## G. 사용자 지역 기준 시각

### G1. `UserSettings.timezone`이 저장만 되고 쓰이지 않는다

**현상**

사용자별 timezone 설정이 양쪽에 이미 배선되어 있으나, 어떤 시각 표시에도 사용되지 않는다.

| 계층 | 상태 |
| --- | --- |
| DB·엔티티 | `UserSettings.timezone` 컬럼 존재, 기본값 `Asia/Seoul` |
| 서비스 | `UserSettingsService.normalizeTimezone`으로 검증 |
| API | `UserSettingsResponse`·`UpdateSettingsRequest`에 포함 |
| 프론트 폼 | `useUserSettingsForm.ts`가 값을 싣고 저장 시 전송 |
| 프론트 UI | **선택 컨트롤 없음.** `UserSettings.vue`에 입력 요소가 없고 i18n 라벨도 없다 |
| 표시 로직 | **소비처 없음.** `date.ts`를 포함해 이 값을 읽는 코드가 없다 |

폼 기본값이 `'Asia/Seoul'`로 하드코딩되어 있어, 저장할 때마다 같은 값이 왕복한다.

**영향**

현재 사용자에게 보이는 오작동은 없다. 다만 컬럼·검증·DTO·폼 필드가 모두 유지 비용을 발생시키면서 아무 기능도 제공하지 않는다. 그리고 이 필드는 E2가 지적한 문제를 푸는 데 필요한 재료이므로, 미사용 상태로 두는 것보다 연결하는 편이 낫다.

### G2. 시각 표시를 사용자 지역 기준으로 전환하는 설계

**결론: 가능하다. 단 "표시"만 사용자 기준으로 하고 "판정"은 서비스 기준(KST)을 유지해야 한다.**

#### 두 종류의 시간을 먼저 구분한다

| 구분 | 예시 | 기준 |
| --- | --- | --- |
| **A형 — 순간** | 게시글·댓글 작성 시각, 알림 발생 시각, 로그인 이력, 쪽지 시각 | **뷰어 지역**으로 표시 |
| **B형 — 서비스 달력 판정** | 출석 체크의 "오늘", 일일 포인트·작성 한도, 인기글 오늘/어제 버킷, 예약 발행 시각 | **KST 고정** |

B형을 뷰어 기준으로 바꾸면 안 된다. 기기 timezone을 바꿔 하루에 두 번 출석하거나 일일 한도를 초기화하는 우회가 생긴다. 예약 발행도 작성자가 지정한 시각의 의미가 조회 시점마다 달라진다. `AttendanceService`가 이미 주입 `Clock`(KST)을 쓰고 있으므로 **B형은 손대지 않는 것이 정답이다.**

#### 선결 조건

현재 wire는 offset이 없는 KST 벽시계 문자열(`"2026-07-25T10:00:00"`)이다. 클라이언트는 이 값이 어느 순간인지 알 수 없으므로, **어떤 렌더링 개선도 이 형식 위에서는 불가능하다.** E2의 근본 원인이자 G2의 1단계다.

#### 단계

1. **wire에 offset을 싣는다.** 저장값이 전부 KST임이 확정되었으므로 DB 변경 없이 직렬화 계층에서 해결된다. `LocalDateTime`을 `atZone(KST_ZONE_ID).toInstant()`로 변환해 `"2026-07-25T10:00:00+09:00"` 또는 `Z` 형식으로 내보낸다. A1의 legacy 허용 목록과 같은 방식으로 DTO별 점진 이행이 가능하다.

2. **프론트 `toDate`를 통일한다.** offset이 붙으면 `new Date(str)`이 명확해지고, 배열 분기(`Date.UTC`)와 문자열 분기가 같은 기준을 갖게 된다. E2의 2단계와 동일한 작업이다.

3. **표시 지역을 결정한다.** 기존 `language` 설정과 같은 구조를 쓴다.
   - 기본: 브라우저 자동 감지 `Intl.DateTimeFormat().resolvedOptions().timeZone`
   - 우선: `UserSettings.timezone`이 명시적으로 설정된 경우 그 값
   - G1의 UI 컨트롤과 i18n 라벨을 이때 함께 추가한다. "자동" 옵션을 기본으로 두면 대부분의 사용자는 설정을 건드릴 필요가 없다.

4. **`date.ts`에 `timeZone`을 전달한다.** `formatter()`가 이미 `Intl.DateTimeFormat`을 쓰고 있어 옵션 하나를 넘기면 된다. `formatTimeAgo`는 절대 시각끼리 빼는 방식이라 offset이 붙는 순간 자동으로 옳아진다.

5. **B형 경계를 문서화한다.** 어떤 값이 서비스 기준이고 어떤 값이 뷰어 기준인지 `API명세서.md`에 명시한다. 이 구분이 코드에 드러나지 않으면 이후 누군가 출석 판정을 뷰어 기준으로 바꾼다.

#### DB 전환은 별도 판단

장기적으로는 `timestamptz` + `Instant`가 정답이지만, **1단계만으로 사용자 지역 표시는 완성된다.** 저장 계층 전환은 마이그레이션 비용과 기존 쿼리 영향이 크므로 G2와 분리해 판단한다. 신규 컬럼부터 `timestamptz`를 쓰는 방식으로 점진 이행할 수 있다.

#### 범위 밖

- 이메일·웹푸시 본문의 시각 표기는 클라이언트 렌더링이 아니므로 별도 처리가 필요하다. 수신자의 `UserSettings.timezone`을 서버에서 읽어 포맷해야 하며, 이 경우에만 서버가 사용자 timezone을 직접 사용한다.
- 관리자 화면의 운영 지표(오류 로그, 감사 로그)는 운영 기준 시각이 자연스러우므로 KST 고정을 검토한다.

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

1. **G2 1단계 + E2** — wire에 offset을 싣는 작업이다. E2의 근본 원인을 없애고 사용자 지역 표시의 선결 조건을 동시에 만든다. DB 변경 없이 직렬화 계층에서 끝나며, 이 문서에서 **현재 사용자에게 영향이 있는 유일한 항목**(KST 밖 사용자의 시각 오표시)을 해소한다.
2. **A2** — 이미 구현·검증된 서버 기능을 클라이언트에 연결한다. 변경 범위가 좁고 효과가 즉시 나타난다.
3. **G2 2~4단계 + G1** — 프론트 `toDate` 통일, 표시 지역 결정, 설정 UI 노출을 함께 처리한다. 1단계가 끝나야 착수할 수 있다.
4. **A1** — 이후 모든 DTO 변경의 재발을 막는 구조적 방어이며 A6의 선행 조건이다. G2 1단계의 점진 이행 목록과 방식이 같으므로 함께 설계하면 작업이 겹치지 않는다.
5. **A4**, **A3** — 사용자에게 보이는 오류 표시와 실시간 채널의 신뢰도를 올린다.
6. **A5** — 서버 정책 설계가 필요하므로 별도 논의를 거친다.
7. **E1**, **E3** — 동작 변화 없는 명시성 정리다. 언제 해도 무방하나 G2 완료 후에 하면 `setDefault` 제거까지 한 번에 판단할 수 있다.
8. **B1**, **B2**, **C1**, **D1** — 국소 변경이며 위 항목과 독립적으로 처리 가능하다.
9. **A6**, **A7** — A1 완료 후 착수한다.
10. **F1**, **F2** — 범위 내 실사용 결함이 아니다. 공용 유틸리티 정리나 수평 확장 계획이 생길 때 착수한다.
