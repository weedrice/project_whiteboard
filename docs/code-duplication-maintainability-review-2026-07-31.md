# 코드 중복 및 유지보수성 검토 (2026-07-31)

## 1. 검토 목적과 범위

프로젝트에서 동작 차이를 만들 수 있는 중복 코드와 책임이 과도하게 모인 지점을 정적 검토했다. 이번 검토는 즉시 대규모 리팩터링을 제안하기보다, 기존 API와 동작을 보존하면서 작은 단계로 개선할 후보를 찾는 데 초점을 맞췄다.

### 포함 범위

- 백엔드 커뮤니티 도메인 및 사용자 기능: `auth`, `board`, `comment`, `feed`, `file`, `message`, `notification`, `post`, `search`, `shop`, `tag`, `user` 등
- 프론트엔드의 공통 UI, 검색, 게시글, 사용자 설정, 메시지 기능

### 제외 범위

- 인프라: `deploy/`, Docker/배포 스크립트, 모니터링 및 운영 자동화
- 광고 도메인: `backend/.../domain/ad`, 광고 관련 프론트엔드 코드
- 에이전트 도메인: `backend/.../domain/agent`, `frontend/src/features/user/agents`
- 생성 코드: `frontend/src/types/generated/api.ts`

검토는 파일 크기와 책임 수, 서비스 간 위임 구조, 유사한 정규화·검증 로직, 비동기 상태 및 취소 처리의 응집도를 중심으로 수행했다. 단순히 코드가 길다는 이유만으로 문제로 분류하지 않고, 변경 시 서로 다른 경로가 다르게 동작할 위험이 확인되는 경우를 우선했다.

## 2. 요약

| 우선순위 | 분류 | 위치 | 결론 |
| --- | --- | --- | --- |
| P0 | 중복으로 인한 동작 불일치 | 검색 기간 계산 | **즉시 수정 권장**. 같은 검색 조건이 일반 검색과 통합 검색에서 서로 다른 날짜 범위와 오류 처리를 사용한다. |
| P1 | 과도한 비동기 책임 | 프론트엔드 메시지 리소스 | 상세 조회, 대화 페이지, 읽음 처리, 실시간 이벤트, 답장, 삭제, 취소가 한 composable에 결합돼 있다. |
| P1 | 과도한 파사드/위임 계층 | `PostService` | 92개 메서드와 다수 하위 서비스를 가진 호환성 파사드가 도메인 변경의 영향 범위를 넓힌다. |
| P2 | 기능 경계 혼재 | 검색 프론트엔드 | 검색 도메인 로직이 루트 composable과 688줄 뷰에 분산돼 feature-slice 규칙과 어긋난다. |
| P2 | 화면 책임 집중 | 사용자 설정 화면 | 여러 독립 설정 기능이 한 681줄 화면에서 상태와 템플릿을 함께 관리한다. |

가장 중요한 발견은 검색 기간 계산의 중복이다. 이는 정리 수준의 문제가 아니라 현재 사용자에게 서로 다른 결과를 반환할 수 있는 결함 후보이므로 다른 구조 개선보다 먼저 다뤄야 한다.

## 3. 상세 발견 사항

### P0. 검색 기간 계산 중복이 실제 동작 불일치를 만든다

**근거**

- 일반 게시글 검색의 `SearchService`는 `WEEK`를 `today.minusWeeks(1).plusDays(1)`부터 계산하고, `MONTH`에도 `plusDays(1)`을 적용한다. 사용자 입력 시작일이 종료일보다 늦으면 `VALIDATION_ERROR`를 반환하며, 알 수 없는 기간 값도 오류로 처리한다 (`SearchService.java:117-150`).
- 통합 검색의 `SearchPreviewReadService`는 `WEEK`를 `today.minusWeeks(1)`, `MONTH`를 `today.minusMonths(1)`부터 계산한다. 사용자 지정 범위의 역전 검증이 없고, 알 수 없는 기간은 빈 범위로 조용히 바꾼다 (`SearchPreviewReadService.java:187-208`).
- 두 경로 모두 종료일 다음 날 00:00을 exclusive upper bound로 사용하므로, 시작일의 하루 차이는 결과 집합의 실제 하루 차이가 된다.

**영향**

- 같은 `period=WEEK` 요청도 `/search/posts`와 통합 검색에서 서로 다른 게시글 수를 반환할 수 있다.
- 잘못된 사용자 지정 기간이 한 경로에서는 400 계열 검증 오류가 되고 다른 경로에서는 빈 결과가 될 수 있다.
- 프론트엔드가 통합 검색 결과와 게시글 전용 검색 결과를 교차 사용하면 페이지 전환 시 결과가 갑자기 늘거나 줄 수 있다.

**권장 조치 — 즉시 적용 후보**

1. 검색 패키지 내부에 하나의 `SearchDateRangeResolver` 또는 값 객체를 두고 두 서비스가 동일하게 사용한다.
2. 기간 의미를 명시한다. 현재 일반 검색 기준인 `TODAY=1일`, `WEEK=오늘 포함 7일`, `MONTH=오늘 포함 달력상 1개월 길이`를 호환 기준으로 삼는 것이 안전하다.
3. `CUSTOM`의 역전 범위와 알 수 없는 기간은 두 API 모두 동일한 `VALIDATION_ERROR`로 처리한다.
4. 일반 검색과 통합 검색을 같은 고정 시각에서 비교하는 파라미터 테스트를 추가한다. `TODAY`, `WEEK`, `MONTH`, `CUSTOM`, 역전 범위, 잘못된 기간을 모두 포함한다.

### P1. 메시지 composable이 비동기 수명주기를 너무 많이 소유한다

**근거**

- 개선 전 `useMailboxResource.ts`는 652줄이며 29개의 함수가 있었다.
- 하나의 composable이 요청 ID 기반 stale 판정, 다수 `AbortController`, 상세 조회, 읽음 처리, 대화 페이지 병합 및 추가 로딩, 답장 후 새로고침, 실시간 알림 중복 제거, 선택 삭제, 답장 UI 상태를 모두 관리한다 (`useMailboxResource.ts:77-180`, `191-318`, `319-495`, `496-575`).
- 특히 상세 요청과 대화 요청은 병렬 실행되지만 서로 다른 오류 상태와 하나의 abort controller를 공유한다. 기능 추가 시 한 요청의 취소 정책이 다른 요청의 UX에 영향을 줄 가능성이 높다 (`useMailboxResource.ts:275-317`).

**영향**

- 읽음 처리, SSE 갱신, 답장 완료가 동시에 일어나는 경합 조건을 국소적으로 이해하기 어렵다.
- 단위 테스트가 전체 composable 상태를 구성해야 하므로 실패 원인과 책임 경계가 불명확해진다.
- 새 대화 기능을 추가할 때 기존 취소·stale 방지 규칙을 누락하거나 중복 구현하기 쉽다.

**권장 조치 — 단계적 설계 필요**

1. 순수 함수인 대화 병합/페이지 변환은 `conversationModel.ts`로 이동한다.
2. 요청 ID와 abort 수명주기는 `useLatestAsyncTask` 같은 기존 공통 도구의 적용 가능성을 먼저 확인하고, 상세과 대화 페이지 요청을 별도 리소스로 분리한다.
3. `useMailboxConversation`, `useMailboxSelection`, `useMailboxRealtimeSync`처럼 상태 소유권별 composable을 만들되, 현재 `useMailboxResource` 반환 형태는 파사드로 유지해 화면 계약을 보존한다.
4. 추출 단계마다 기존 `useMailboxResource.spec.ts`의 취소, stale 응답, 실시간 갱신, 답장 후 갱신 테스트를 먼저 고정한다.

### P1. `PostService`가 호환성 파사드와 도메인 서비스 역할을 동시에 수행한다

**근거**

- 개선 전 `PostService`는 450줄, 92개 public/private 메서드이며 15개 안팎의 저장소·정책·하위 서비스를 주입받았다 (`PostService.java:43-63`).
- 목록 조회는 `PostListReadService`, 작성은 `PostCommandService`, 상호작용은 `PostInteractionService`, 상세 조회는 `PostDetailReadService`, 시리즈는 `PostSeriesService`로 이미 책임이 분리돼 있지만, 다수 호출자가 다시 `PostService`를 통한다 (`PostService.java:65-152`, `180-215`).
- 일부 메서드는 단순 위임인데도 파사드에 트랜잭션 선언이 반복돼 실제 트랜잭션 소유자가 파사드인지 하위 서비스인지 추적해야 한다 (`PostService.java:82-100`, `184-201`).

**영향**

- 작은 기능 변경도 거대한 파사드의 의존성과 테스트 fixture를 건드릴 가능성이 높다.
- 하위 서비스가 이미 제공하는 좁은 API 대신 포괄 서비스가 재사용되면서 새 순환 의존성이나 불필요한 의존성이 생기기 쉽다.
- 트랜잭션 전파와 권한 검증 책임이 위임 경계 양쪽에 분산될 수 있다.

**권장 조치 — 단계적 설계 필요**

1. `PostController`처럼 API 호환 파사드가 유용한 호출자는 유지한다.
2. `FeedService`, `HomeLandingService`, `TagController`, `UserController`, `BoardQueryService`의 사용 메서드를 분류하고, 읽기 전용 내부 호출부터 해당 좁은 서비스로 직접 의존하게 한다.
3. 호출자가 사라진 위임 메서드와 불필요한 저장소 의존성을 한 번에 하나씩 제거한다.
4. 트랜잭션은 실제 변경을 수행하는 command service가 소유하도록 확인하되, 기존 propagation 동작을 테스트 없이 일괄 변경하지 않는다.

이 항목은 한 번의 대형 리팩터링 대상으로 보지 않는다. 현재 파사드는 기존 호출자의 안정성을 제공하므로, 소비자별로 이동하고 dead code를 즉시 제거하는 방식이 적합하다.

### P2. 검색 프론트엔드의 도메인 로직이 feature 경계 밖에 남아 있다

**근거**

- `useSearch.ts`는 검색 API 호출, 응답 view model 변환, 검색 query key와 인증 범위 meta를 모두 소유하지만 루트 `src/composables`에 있다 (`useSearch.ts:1-18`, `42-68`, `70-152`).
- 검색 라우트 상태와 제출 이동 로직도 `useSearchRouteQuery`, `useSearchSubmitNavigation`, `useSearchNavigation` 등 여러 루트 composable에 흩어져 있다.
- `SearchPage.vue`는 688줄이며 결과 렌더링, 필터 폼, URL 상태, 키워드/시맨틱 결과 조합, 인기 검색어까지 한 화면에서 조립한다. 현재 직접 `@/composables/useSearch`를 가져온다 (`SearchPage.vue:390-393`).

**영향**

- 공통 저수준 composable과 검색 도메인 구현의 경계가 흐려져 루트 composable이 계속 커질 수 있다.
- 검색 API 모델, 라우팅, 화면 변경의 영향 범위를 찾기 어렵다.
- 저장소의 feature-slice 관례와 달라 새 검색 코드를 어디에 추가해야 하는지 불명확하다.

**권장 조치 — 낮은 위험의 구조 정리**

1. `src/features/search/{queries,navigation,model}` 경계를 만들고 검색 전용 composable과 query key를 이동한다.
2. 먼저 재수출 호환 파일을 두거나 모든 소비자를 같은 커밋에서 이동해 import 계약을 명확히 한다.
3. `SearchPage`에서는 결과 섹션과 필터 패널을 표현 컴포넌트로 추출하되, URL query의 단일 소유권은 페이지 composable에 유지한다.
4. 이동 후 기존 검색 페이지 및 composable 테스트의 import 경로도 feature 경계로 맞춘다.

### P2. 사용자 설정 화면이 독립적인 기능 영역을 한 파일에서 조정한다

**근거**

- 개선 전 `UserSettings.vue`는 681줄이고 script 영역만 337줄이었다.
- 일반 설정, 알림 유형, 키워드 구독, 웹 푸시, 세션, 로그인 기록처럼 별도의 API와 실패 상태를 가진 기능이 하나의 화면 상태에 결합돼 있다 (`UserSettings.vue:53-144`, `180-218`).
- 저장/재시도/검증 메시지와 섹션 표시 상태가 화면 상단에 함께 있어 특정 섹션 변경도 전체 화면 회귀 검토를 요구한다.

**영향**

- 독립 섹션의 로딩 또는 오류 정책 변경이 공통 `loading`, `criticalLoadError` 판단에 영향을 줄 수 있다.
- 템플릿과 비동기 mutation 연결을 한 파일에서 검토해야 해 접근성 및 상태 회귀를 놓치기 쉽다.

**권장 조치 — 화면 계약을 보존한 추출**

1. 이미 분리된 계정/프로필 컴포넌트 패턴을 따라 알림, 키워드 구독, 세션/로그인 기록을 섹션 컴포넌트로 추출한다.
2. 각 섹션이 자체 mutation pending/error 상태를 소유하게 하고, 페이지는 활성 섹션과 공통 설정 저장만 조정한다.
3. 추출 전 `UserSettings.spec.ts`에서 각 섹션의 성공·실패·재시도·비활성 상태를 계약 테스트로 유지한다.

## 4. 이미 잘 적용된 부분

다음은 중복 후보처럼 보이지만 현재 구조를 유지하는 편이 낫거나, 이미 적절히 분리된 부분이다.

- 게시글 도메인은 목록, 상세, command, interaction, series, related read 서비스로 실제 비즈니스 책임이 상당 부분 분리돼 있다. 문제는 분리 자체가 아니라 오래된 포괄 파사드의 소비자가 아직 넓다는 점이다.
- 댓글 도메인은 `CommentQueryService`와 `CommentCommandService`로 읽기/쓰기를 분리하고 `CommentService`는 비교적 얇은 호환 파사드로 유지한다 (`CommentService.java:16-111`). 이는 `PostService` 개선 시 참고할 수 있는 중간 상태다.
- 검색 요청 문자열 정규화는 `SearchRequestNormalizer`로 이미 모였다. 날짜 범위 계산도 같은 방식으로 중앙화하면 작은 변경으로 불일치를 제거할 수 있다.
- 프론트엔드에는 `useDebounce`, `useLatestAsyncTask`, query key 모듈 등 재사용 기반이 존재한다. 타이머가 보인다는 이유만으로 모두 합치지 말고, 동일한 수명주기 의미를 가진 경우에만 재사용해야 한다.

## 5. 실행 결과

### 1단계: 검색 동작 불일치 수정

- `SearchDateRangeResolver`를 도입해 두 검색 서비스의 기간 계산과 검증을 통일했다.
- 고정 날짜 기반 기간별·오류 경로 테스트와 통합 검색 회귀 테스트를 추가했다.
- 커밋: `efec092ff` (`Fix: 검색 기간 계산 규칙 통일`)

### 2단계: 메시지 비동기 모델 분리

- 순수 병합/페이지 모델을 `conversationModel.ts`로 추출했다.
- `useMailboxRequestLifecycle`와 `useMailboxRealtimeSync`로 요청 취소·세션 유효성·SSE 중복 제거를 분리했다.
- 기존 `useMailboxResource` 반환 계약과 화면 동작을 유지했다.
- 커밋: `b4faf762f`, `6b7347121`

### 3단계: 프론트엔드 feature 경계 정리

- 검색 query와 navigation 로직 및 테스트를 `features/search/{queries,navigation}`로 이동하고 모든 소비자 import를 갱신했다.
- 사용자 설정을 일반, 알림·키워드·푸시, 보안 섹션 컴포넌트로 분리했다.
- 각 설정 섹션이 자체 query, mutation, loading, error 상태를 소유하고 페이지는 탭과 이탈 방지 상태만 조정한다.
- 커밋: `559413d85`, `9466b0c62`

### 4단계: `PostService` 소비자 축소

- `FeedService`, `HomeLandingService`, `TagController`, `UserController`, `BoardQueryService`를 실제 읽기 소유 서비스에 직접 연결했다.
- 호출자가 사라진 `PostService` 읽기 위임 메서드 10개를 제거했다.
- 기존 테스트 fixture는 좁은 서비스를 직접 검증하도록 전환해 검증 범위를 유지했다.
- 커밋: `9b084f74c`, `7368d634a`

## 6. 적용 여부 분류

| 항목 | 분류 | 비고 |
| --- | --- | --- |
| 검색 기간 계산 중앙화 | **적용 완료** | 공통 resolver로 기간 계산과 검증을 통일하고 고정 날짜 기반 파라미터 테스트를 추가했다. |
| 메시지 composable 분리 | **적용 완료** | 대화 모델, 요청 수명주기, 실시간 동기화를 분리하고 기존 파사드 반환 계약을 유지했다. |
| `PostService` 소비자 축소 | **적용 완료** | 내부 읽기 소비자 5곳을 좁은 서비스로 전환하고 dead delegation 10개를 제거했다. |
| 검색 feature slice 이동 | **적용 완료** | query, query key, route/navigation 로직과 테스트를 `features/search`로 이동했다. |
| 사용자 설정 섹션 분리 | **적용 완료** | 섹션별 컴포넌트가 query·mutation·오류·로딩 상태를 소유하도록 분리했다. |
| 검색 문자열 정규화 분리 | **이미 적용됨** | `SearchRequestNormalizer`가 공통 정책을 소유한다. |
| 댓글 command/query 분리 | **이미 적용됨** | 얇은 파사드 아래 읽기/쓰기 책임이 분리돼 있다. |

## 7. 보안 및 작업 트리 참고

- 구현 재개 시 `backend/src/main/resources/application-dev.yml`에 기존 사용자 변경이 남아 있었다.
- 해당 파일은 로컬 환경 및 secret 계열 설정 가능성이 있는 경로이므로 값을 문서에 기록하지 않았고 어떤 작업 커밋에도 포함하지 않았다.
- 개선은 기존 API URL, 응답 DTO, DB 스키마와 화면 계약을 유지하는 책임 분리 및 검색 기간 오류 일관성 수정으로 한정했다.

## 8. 추가 개선 적용

초기 개선 이후 남은 대형 파일과 반복 정책을 다시 검토해 다음 책임을 추가로 분리했다.

| 작업 단위 | 적용 내용 | 커밋 |
| --- | --- | --- |
| 댓글 조회 정책 | 댓글 스레드 JPQL의 공통 projection·join·filter를 중앙화하고 정렬 규칙만 저장소 메서드별로 유지했다. | `43370efbd` |
| 게시글 댓글 실시간 동기화 | `PostDetail.vue`가 직접 관리하던 SSE 연결, 재연결, 해제, 갱신 debounce를 전용 composable로 옮겼다. | `d11a71143` |
| 사용자 기능 composable | 프로필, 설정, 보안, 에이전트, 활동 기능이 각자 상태와 요청을 소유하고 `useUser`는 호환 파사드로 유지했다. | `e9fa9e24b` |
| 게시글 상호작용 | 반응, 스크랩, 열람 이력을 별도 도메인 서비스로 분리하고 기존 상호작용 서비스는 접근 확인과 위임을 담당하게 했다. | `332f3698f`, `046e3cd00` |
| SSE 댓글 topic | 테스트의 내부 Map 리플렉션 의존을 관찰 계약으로 교체한 뒤 topic·board 인덱스와 정리 정책을 레지스트리로 분리했다. | `1541304e4`, `7fc3dd2d8` |
| 인증 갱신 조정 | 탭 간 메시지 프로토콜과 localStorage lease/fencing을 각각 순수 모듈로 분리하고 coordinator는 상태 전이를 담당하게 했다. | `68be6c4cd` |
| 게시글 폼 시리즈 | 시리즈 조회, 생성, 취소, 세션·폼 identity, 캐시 갱신 수명주기를 전용 composable로 옮겼다. | `be347a7b0` |
| 전역 예외 처리 | validation 오류 수집과 오류 로그 전송을 보조 객체로 분리했다. MVC slice 호환성을 위해 기존 의존성으로 내부 조립하는 경계를 유지했다. | `727539e51`, `bf2b4a483` |
| 검증 환경 안정화 | 이동한 사용자 설정 경로를 i18n 가드에 반영하고, OpenAPI 스냅샷 비교가 Windows CRLF를 계약 변경으로 오인하지 않게 했다. | `a0209659c`, `4c8e67a96` |

## 9. 추가 검토 결론

- 이번 범위에서 확인한 우선순위 높은 중복 정책과 과도한 책임 집중은 모두 적용했다.
- 기존 공개 API, DTO, DB 스키마, Vue 컴포넌트의 외부 props·emit 계약은 변경하지 않았다.
- 더 작은 파일을 만들기 위한 기계적 분리는 진행하지 않았다. 새 경계가 상태, 요청 수명주기, 도메인 정책 중 하나를 명확히 소유하는 경우에만 추출했다.
- `PostService`, `CommentService`, `useUser`, `PostInteractionService` 같은 기존 진입점은 호출자 호환성이 있는 파사드로 남겼다. 소비자 전환이 끝난 메서드만 후속 작업에서 제거하는 것이 안전하다.
- `application-dev.yml`의 기존 사용자 변경은 계속 작업 범위에서 제외했다.
