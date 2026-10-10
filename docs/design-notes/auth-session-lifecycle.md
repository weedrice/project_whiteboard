# 인증 세션과 비동기 작업의 생명주기

## 문서 상태와 범위

현재 구현된 프런트엔드 세션 격리와 백엔드 토큰 계약의 설계 이유·영향 경로를 설명한다. 변경 규칙은 [frontend/AGENTS.md](../../frontend/AGENTS.md), [backend/AGENTS.md](../../backend/AGENTS.md), [백엔드 인증 AGENTS.md](../../backend/src/main/java/com/weedrice/whiteboard/domain/auth/AGENTS.md)를 따른다. 운영 배포 완료나 모든 호출부의 자동 보호를 선언하지 않는다.

| 상태 | 범위 |
| --- | --- |
| 적용됨 | 공통 세션 scope, Vue Query mutation 보호, `expectedSession`을 받는 HTTP 요청·응답·refresh/replay 검사 |
| 적용됨 | 공통코드 수동 저장·삭제의 scope 적용과 이모티콘 상세·편집·라우터 캐시의 세션 격리 |
| 적용됨 (2026-10-10) | 확인·입력창 취소, 작업 ID 기반 pending, 로컬 상태 reset, 행동·업로드·일반/무한/라우터 조회의 시작 의도 전달 |
| 적용됨 (2026-10-10) | 이전 숫자 sessionId에서 활성 family를 찾아 종료; API/DTO 형태 유지 |
| 후속 설계 | 과거 토큰 행 삭제 이후에도 유지되는 별도 관리 식별자의 API 전환 |
| 보장하지 않음 | 이미 서버에 도달한 변경의 롤백, 프런트 검사만으로 하는 서버 권한 검증, 모든 오래된 Promise의 즉시 종료 |

## 서로 다른 세션 식별자

프런트의 `sessionGeneration`은 현재 브라우저 실행 안에서 이전 작업과 새 작업을 구분하는 값이다. 백엔드 refresh token의 `sessionFamilyId`와 같은 식별자가 아니며 서버 인증 정보로 전송하는 값도 아니다. 프런트 작업 의도는 세대와 사용자 ID를 함께 캡처한다.

- [authSessionIntent.ts](../../frontend/src/utils/authSessionIntent.ts)는 `{ sessionGeneration, userId }`를 캡처하고 현재 값과 비교한다. 같은 사용자로 다시 로그인해도 세대가 바뀌면 이전 작업은 오래된 작업이다.
- [auth.ts](../../frontend/src/stores/auth.ts)의 `applyTokenIfCurrent`는 예상 세대와 이전 토큰이 일치할 때 새 access token을 반영하며 세대를 증가시키지 않는다. 정상 401 refresh 중인 동일 세션의 작업을 이어가기 위한 경로다.
- 같은 파일의 `applyAuthenticatedSession`, `clearSessionState`, `setTokens`, `applyNewSessionIfCurrent` 등은 필요한 경우 `advanceSessionGeneration`을 거친다. 따라서 모든 토큰 변경을 동일 세션 갱신으로 취급해서는 안 된다.
- `onSessionBoundary`와 `onPrincipalChange`는 구분된다. 부트스트랩은 세대 경계를 만들 수 있지만 사용자 주체 변경에 따른 기기 저장값 정리를 항상 수행하지는 않는다.
- [main.ts](../../frontend/src/main.ts)는 세션 경계 통지, 알림 스트림 상태 초기화, 인증 범위 query 정리를 연결한다. [authBoundaryChannel.ts](../../frontend/src/api/authBoundaryChannel.ts)와 auth store는 탭 사이의 로그인·로그아웃 경계를 전달하고 처리한다.

사용자 ID만 비교하면 동일 계정 재로그인을 구분하지 못한다. access token 문자열만 비교하면 정상 refresh까지 다른 세션으로 취급하게 된다.

## 토큰 저장과 백엔드 계약

프런트 access token은 [authTokenStorage.ts](../../frontend/src/utils/authTokenStorage.ts)의 메모리 저장소를 사용한다. 이전 localStorage 토큰 키는 정리하며, refresh token을 프런트 저장소로 옮기지 않는다. 공유 [Axios client](../../frontend/src/api/index.ts)는 쿠키를 포함하는 요청을 사용한다.

| 구현 | 확인된 역할 |
| --- | --- |
| [AuthController](../../backend/src/main/java/com/weedrice/whiteboard/domain/auth/controller/AuthController.java) | 로그인·refresh 응답 본문에는 access token과 만료 정보를 반환하고 refresh token은 쿠키 writer로 전달한다. 로그아웃은 해당 쿠키를 읽어 처리한 뒤 정리한다. |
| [RefreshTokenCookieWriter](../../backend/src/main/java/com/weedrice/whiteboard/global/security/RefreshTokenCookieWriter.java) | `refreshToken` 쿠키에 HttpOnly, SameSite=Lax, `/api/v1/auth` 경로를 사용한다. `prod`에서는 Secure를 강제하고 다른 환경에서는 요청 정보를 사용한다. 과거 쿠키 경로도 정리한다. |
| [SessionTokenService](../../backend/src/main/java/com/weedrice/whiteboard/domain/auth/service/SessionTokenService.java) | refresh에서 기존 토큰을 폐기하고 같은 세션 family로 새 토큰을 발급한다. 폐기 토큰 재사용과 계정 자격 실패 처리에는 family 폐기 및 커밋 이후 알림 연결 해제가 포함된다. 로그아웃도 해당 family를 폐기한다. |

토큰·쿠키·OAuth 변경의 교차 검토 범위는 각 모듈 지침을 따르며, 위 구현에서 실제 계약을 확인한다.

## 작업 시작 시 세션 캡처

A 계정에서 시작한 작업이 확인창, 오프라인 대기, 비동기 준비를 지나 B 계정에서 전송되면 요청 인터셉터가 보는 현재 토큰은 B의 토큰이다. 전송 시점의 신원만 저장하면 A가 시작한 작업이라는 사실을 복구할 수 없다.

[authSessionScope.ts](../../frontend/src/utils/authSessionScope.ts)의 `createAuthSessionScope`는 호출 순간의 신원을 동기적으로 캡처한다. scope는 원래 의도가 들어간 `requestConfig`, 호출자 config와 signal을 합치는 `withRequestConfig`, `assertCurrent`, `isCurrent`, 동기 UI 변경용 `runIfCurrent`, 명시적 취소용 `cancel`, 정리용 `dispose`를 제공한다.

세션 경계에서는 scope의 signal을 취소한다. `dispose`는 리스너를 해제하며, 성공적으로 끝난 요청을 뒤늦게 취소하지 않는다. 현재 구현은 native AbortSignal과 abort 이벤트를 제공하는 Axios GenericAbortSignal의 취소를 결합하고, 이미 취소된 signal도 유지한다.

검사는 동기 구간에만 유효하다. `runIfCurrent`에 비동기 callback을 넣어도 내부 await 이후는 보호되지 않으며, mutation이나 수동 action 내부의 후속 부수 효과 전에 다시 검사해야 한다. 페이지·선택 항목 revision은 세션과 다른 경계를 보호하므로 별도로 유지한다.

### Vue Query mutation

[useAuthScopedMutation.ts](../../frontend/src/composables/useAuthScopedMutation.ts)는 `mutate`와 `mutateAsync` 호출 시 scope를 만든다. Vue Query의 전역·로컬 `onMutate`가 기다리거나 mutation이 오프라인으로 중단되기 전에 캡처된다.

- mutation 함수 전후와 callback 진입 등에서 의도를 검사한다. 오래된 실행의 `mutateAsync`는 `AbortError`로 거절하며, `mutate`는 내부에서 rejection을 처리한다. 각 callback 내부 await 이후는 위 호출부 검사 규칙을 따른다.
- hook 및 per-call 성공·오류·종료 callback의 마지막 context에도 `intent`, `requestConfig`, `assertCurrent`, `isCurrent`, `runIfCurrent`를 전달한다. optimistic context의 위치와 의미는 유지한다.
- `onCleanup`은 세션이 바뀌어도 한 번 실행되는 자원 정리 경로다. 토스트·탐색·새 세션의 UI 갱신을 넣는 경로가 아니다.
- 컴포넌트가 unmount되어도 같은 세션의 mutation은 완료되고 공유 캐시를 갱신할 수 있다. scope 리스너는 mutation이 정산될 때 해제된다.
- 오프라인으로 멈춘 mutation은 세션 경계에서 Promise가 즉시 끝난다고 보장하지 않는다. 재개 시 저장된 의도가 다시 검사된다.

### 수동 다단계 작업

`runAuthScopedAction`은 동일 scope를 수동 작업에 제공한다. 세션이 바뀐 작업의 결과·오류는 `undefined`로 조용히 정리하고, 현재 세션의 실제 실패는 호출자에게 전달하며, 종료 시 항상 scope를 정리한다.

[CommonCodeManagement.vue](../../frontend/src/views/admin/CommonCodeManagement.vue)는 확인창 전에 scope를 만들고 쓰기 API에 `scope.requestConfig`를 전달한다. HTTP 완료, 재조회, 캐시 무효화 뒤에도 필요한 시점에 검사하여 이전 작업이 새 계정의 모달이나 저장 잠금을 변경하지 않도록 한다.

[프로필 저장](../../frontend/src/features/user/profile/useProfileUpdateSubmit.ts)은 첫 await 전에 pending을 설정하고 함수 진입점에서 재제출을 차단한다. 확인창·이미지 업로드·최종 저장·사용자 재조회가 진행되는 동안 Enter 제출도 같은 잠금을 따른다. 실패·확인 취소 후에는 다시 저장할 수 있고, 세션 경계에서는 기존 revision 검사로 이전 작업의 finally가 새 작업의 pending을 해제하지 못하게 한다.

로그인·로그아웃·부트스트랩은 정상 동작 자체가 세션을 변경할 수 있으므로 일반 업무 action wrapper에 일괄 편입하지 않는다. 이전 세션의 명시적 토큰으로 수행하는 제한된 정리 요청도 기존 인증 생명주기의 의도를 보존해야 한다.

## HTTP 전송·응답·재시도 경계

[apiExpectedSession.ts](../../frontend/src/api/apiExpectedSession.ts)는 `expectedSession`이 있는 요청을 검사한다. 현재 store를 얻을 수 없거나 의도가 다르면 보호 대상 요청을 진행하지 않는다. `expectedSession`이 없는 기존 요청에 시작 의도를 자동 생성하는 방식은 아니다.

| 경계 | 구현과 책임 |
| --- | --- |
| 요청 헤더 적용과 응답 반환 | [apiInterceptors.ts](../../frontend/src/api/apiInterceptors.ts): 원래 세션 검증 뒤 현재 세션 토큰 적용 또는 결과 전달 |
| 오류와 토스트·탐색 | [apiResponseErrorHandler.ts](../../frontend/src/api/apiResponseErrorHandler.ts): 오래된 요청의 오류 처리 제한 |
| refresh 대기·토큰 반영·재전송 | [apiRefreshRetry.ts](../../frontend/src/api/apiRefreshRetry.ts): 원래 `expectedSession`을 유지하고 대기·refresh·사용자 hydration 이후 재검사 |
| 공유 refresh 조정 | [authRefreshCoordinator.ts](../../frontend/src/api/authRefreshCoordinator.ts): refresh 조정과 취소를 소유하며 feature에서 복제하지 않음 |

공유 refresh의 응답·토큰 반영·사용자 hydration에서는 원래 세션 신원을 검사하되, 최초 요청의 개별 signal은 적용하지 않는다. 화면 이탈로 최초 조회만 취소되어도 같은 세션의 다른 대기 요청은 새 토큰으로 재전송할 수 있어야 한다. 개별 signal은 공유 대기열 정산 이후 해당 요청의 재전송을 제한하며, 실제 세션 변경과 refresh 자체의 실패는 공유 작업에 계속 적용된다.

API facade가 config를 받지 않거나 전달하지 않으면 호출부의 캡처가 전송 보호로 이어지지 않는다. 따라서 feature뿐 아니라 `src/api`의 최종 메서드까지 config 전달을 확인한다.

## 캐시와 로컬 상태의 경계

[queryAuthScope.ts](../../frontend/src/queryAuthScope.ts)의 세션 키는 `['session', generation, ...domainKey]` 형식이다. [useApiQuery.ts](../../frontend/src/composables/useApiQuery.ts)는 `AUTH_SCOPED_QUERY_META`가 지정되면 이 키를 계산한다. raw `useQuery`, `useInfiniteQuery`, `fetchQuery` 경로는 [authScopedQuery.ts](../../frontend/src/utils/authScopedQuery.ts)의 옵션 생성기로 같은 키·메타·시작 의도·signal 계약을 적용한다. 메타데이터만 붙인 raw query는 자동 보호되지 않는다. 공통 helper의 request context에 있는 `requestConfig`를 API facade까지 전달한다.

공유 query helper는 인증 범위 query에서 이전 결과를 재사용하는 `keepPreviousData`도 비활성화한다. raw query는 키뿐 아니라 placeholder와 로컬 데이터 복사 경로도 확인해야 한다.

뷰어에 따라 데이터 또는 접근 가능 여부가 달라지는 응답은 공개 URL에 있어도 세션에 종속될 수 있다. 숨김 이모티콘 상세가 그 사례다. 구매 상태만 분리하고 상세를 전역 캐시에 두면 계정 전환 후 서버가 거부할 상세가 브라우저에 남는다.

키 변경 시 다음 경로를 함께 확인한다.

1. [상세 query](../../frontend/src/features/emoticon/detail/useEmoticonDetailResource.ts)와 [편집 query](../../frontend/src/features/emoticon/form/useEmoticonEditResource.ts).
2. [라우터 소유권 검사와 prefetch](../../frontend/src/router/resourceAccessGuards.ts).
3. 구매, [숨김 전환](../../frontend/src/features/emoticon/useToggleEmoticonVisibility.ts), [편집 저장](../../frontend/src/features/emoticon/form/useEmoticonEditSubmit.ts)의 캐시 무효화.
4. [SSE 판매 상태 이벤트](../../frontend/src/features/notifications/stream/notificationStreamController.ts)와 [관리자 판매 상태 변경](../../frontend/src/features/admin/shop/useAdminShopItems.ts)의 무효화.
5. query 데이터에서 복사한 [편집 폼과 이미지](../../frontend/src/features/emoticon/form/useEmoticonEditForm.ts), 아직 끝나지 않은 확인창의 유효성.

query-key 계약의 공유 범위는 [feature 경계 규칙](../../frontend/src/features/AGENTS.md#public-contracts-and-dependencies)을 따른다.

이모티콘 공개 상태 변경은 호출 시 캡처한 대상 ID와 세대의 상세·구매 상태 및 공개 목록 캐시를 갱신한다. 같은 세션에서 다른 상세로 이동하거나 컴포넌트가 해제되어도 성공한 변경의 캐시 갱신은 유지하고, 현재 화면과 관계없는 토스트만 억제한다. 전송 취소와 세션 리스너의 정리는 공통 mutation scope가 소유하므로 화면 해제 뒤 계정이 바뀌어도 이전 결과는 차단된다.

## 적용 상태: 확인창·작업 상태·조회·업로드

2026-10-07 검토에서 제안했던 확장을 2026-10-10에 적용했다. 아래는 당시 후보별 구현 상태이며, 기존 P1 수정(비밀번호 재설정 후보 조회와 이모티콘 파일 소유권)은 별도 변경이다. 모든 비동기 작업의 자동 보호를 의미하지 않으며 새 호출부도 시작 의도와 내부 await 이후 검사 계약을 따라야 한다.

공통 보호는 **행동 시작 → 확인·전처리·대기열 → 전송·재시도 → 결과 반영 → 상태·자원 정리**까지 이어진다. 데이터 소유권, 편집 대상 revision, 서버 작업의 성공 여부는 기능별로 판단한다.

### 공통 도구

| 도구 | 적용된 책임 |
| --- | --- |
| [useAuthScopedOperation](../../frontend/src/composables/useAuthScopedOperation.ts) | 첫 await 전 scope 캡처, key별 중복 실행 차단, 세션 경계·unmount에서 UI pending 해제, 오래된 finally가 새 작업을 해제하지 않도록 ID 비교 |
| [useAuthSessionReset](../../frontend/src/composables/useAuthSessionReset.ts) | 세대·사용자 변경과 경계 통지에 로컬 reset 연결, scope dispose 시 구독 해제; 정상 access-token 회전은 유지 |
| [confirm](../../frontend/src/stores/confirm.ts) / [prompt](../../frontend/src/stores/prompt.ts) | 대화 시작 의도를 저장하고 경계·store dispose에서 미결 Promise를 false/null로 정산하고 입력 초기화 |
| [authScopedQuery](../../frontend/src/utils/authScopedQuery.ts) / [useApiQuery](../../frontend/src/composables/useApiQuery.ts) | 일반·무한·명령형 조회의 세션 키·메타·expectedSession·query signal 결합, 지연 요청과 응답 검사 |
| [useAuthScopedMutation](../../frontend/src/composables/useAuthScopedMutation.ts) | callback에도 세션 context 제공; 자원 정리용 onCleanup과 현재 UI callback 분리 |

수동 UI scope가 unmount 때 취소되어도 하위 Vue Query mutation은 같은 세션에서 정산되어 공유 캐시를 갱신할 수 있다. 오프라인에서 멈춘 mutation Promise의 즉시 정산을 보장하지 않으므로 UI pending은 별도 operation 소유권으로 관리한다.

[새 문의](../../frontend/src/views/inquiry/InquiryNew.vue)는 성공한 첨부 확정과 목록 캐시 갱신을 유지하면서, 컴포넌트가 해제되면 폼 변경과 상세 화면 이동을 중단한다. 캐시 갱신이나 탐색을 기다린 뒤에도 컴포넌트 소유권과 원래 세션을 다시 확인한다.

반응형 조회는 같은 세대에서 이미 확인된 사용자 A가 B로 바뀌어도 최초 신원을 유지하여 요청을 차단한다. 로그인 초기화 중 `null → user` hydration은 같은 세션의 신원 확정으로 허용한다. 일반적인 로그인·계정 전환은 기존 auth store처럼 세대를 증가시킨다. 재시도는 세대뿐 아니라 전체 domain query key도 검사하여 이전 페이지·게시판 요청이 새 입력으로 실행되지 않도록 한다.

[앱 설정 동기화](../../frontend/src/features/user/settings/useAppUserSettingsSync.ts)의 명령형 조회는 access token만 복원되고 사용자 ID가 미확정인 동안 기다렸다가, `/me`로 신원이 확정되면 시작한다. 캐시에서 받은 설정과 지연 로케일 적용도 조회를 시작한 사용자·세대·호출 순서가 현재인지 검사한다. 같은 사용자 정보를 재조회하거나 정상적으로 토큰을 회전하는 동작은 설정 조회를 다시 시작하지 않는다.

### 기능별 적용

| 검토 대상 | 적용 결과 |
| --- | --- |
| [게시글 작업](../../frontend/src/features/board/posts/detail/usePostDetailActions.ts) | 좋아요·스크랩 pending을 operation에 연결하고 신고를 mutateAsync로 정산; 신고 모달 revision과 대상 ID 유지 |
| [댓글 삭제](../../frontend/src/components/comment/CommentList.vue), [이모티콘 숨김](../../frontend/src/features/emoticon/detail/useEmoticonDetailActions.ts), [에이전트 연결·중지](../../frontend/src/features/user/agents/useMyAgentActions.ts) | 확인 전 의도 캡처, 후속 실행 검사, 이전 세션 오류 표시 억제와 로컬 토큰·pending 정리 |
| [문의 상세](../../frontend/src/views/inquiry/InquiryDetail.vue), [새 문의](../../frontend/src/views/inquiry/InquiryNew.vue), [관리자 문의](../../frontend/src/views/admin/AdminInquiryPosts.vue) | 행동 scope, 폼·첨부 reset, 정리 요청의 원래 의도 유지 |
| [문의 업로더](../../frontend/src/components/inquiry/InquiryImageUploader.vue), [인증 이미지](../../frontend/src/components/inquiry/AuthenticatedInquiryImage.vue) | 업로드 batch 신원 유지, 남은 파일 실행 차단, 미리보기 Blob 해제·지연 응답 억제 |
| [에디터 큐](../../frontend/src/composables/useEditorImageUploadQueue.ts), [에디터 업로드](../../frontend/src/features/board/posts/editor/useEditorImageUpload.ts), [에디터](../../frontend/src/components/board/PostEditorTipTap.vue) | enqueue 시 세션 캡처, 세션·작성 대상 변경 시 진행/대기/실패 항목 정리, 이전 파일의 새 세션 재시도 차단 |
| [이모티콘 업로드 세션](../../frontend/src/features/emoticon/form/useEmoticonUploadSession.ts), [등록](../../frontend/src/features/emoticon/form/useEmoticonRegisterSubmit.ts), [수정](../../frontend/src/features/emoticon/form/useEmoticonEditSubmit.ts) | 업로드부터 최종 쓰기까지 동일 requestConfig 전달; 최종 연결 응답이 불확실한 파일의 즉시 삭제 억제 |
| [프로필 저장](../../frontend/src/features/user/profile/useProfileUpdateSubmit.ts), [메시지 전송](../../frontend/src/features/user/messages/useMessageSubmit.ts) | 기존 revision·취소 보호를 유지하면서 전송·파일 정리에도 expectedSession 전달 |
| [관리자 설정](../../frontend/src/features/admin/settings/useGlobalSettingsManager.ts), [IP 차단](../../frontend/src/features/admin/security/useAdminIpBlocksManager.ts), [관리자 관리](../../frontend/src/views/admin/AdminManagement.vue), [오류 로그](../../frontend/src/features/admin/error-logs/useErrorLogDetailModal.ts) | 작업 상태 공통화, 설정 초안·상세·메모 초기화, 지연 완료의 새 UI 변경 방지 |
| [라우터 권한 조회](../../frontend/src/router/resourceAccessGuards.ts), 게시판·게시글·댓글·피드·홈·출석·프로필·에이전트·온보딩 조회 | 일반/무한/fetchQuery에 시작 의도와 취소 전달; 기존 domain key와 mutation/optimistic/SSE 무효화 계약 유지 |
| [작성 중 이탈](../../frontend/src/features/board/posts/form/usePostFormLeaveGuard.ts) | 확인 대기 중 세션이 바뀌면 이전 폼을 flush하지 않고 라우터가 현재 권한을 재평가하도록 허용; 명시적 로그아웃 확인도 회귀 검증 |

### 서버 세션 종료

[UserSessionService](../../backend/src/main/java/com/weedrice/whiteboard/domain/user/service/UserSessionService.java)의 목록은 계속 refresh token 행 ID를 숫자 `sessionId`로 반환한다. 단일 종료는 사용자 잠금과 선택 행의 소유권 확인 후, 같은 family에 활성 토큰이 남아 있는지 검사하여 family를 폐기한다. 목록 조회 후 refresh로 원행이 폐기·만료되어도 이어진 세션을 종료할 수 있다.

현재 기기 여부는 검증된 Authentication의 family로 판단하고, 커밋 후 해당 family의 알림 연결을 해제한다. 타인·없는 행·활성 토큰이 없는 family는 기존 404를 유지한다. DB 스키마와 DTO 변경은 없다.

과거 토큰 행이 정리되어 없어졌다면 이전 숫자 ID로 family를 찾을 수 없으므로 목록을 다시 조회해야 한다. 행 정리와 무관한 관리 식별자를 API에 노출하는 장기 전환은 별도 설계 대상으로 남긴다.

### 기존 보호와 한계

- 초안의 version·clientDraftKey·tombstone·다른 탭 조정·예약발행 보호는 기존 상태기계를 유지한다. 폼을 일괄 재마운트하거나 모든 저장값을 지우지 않는다.
- 로그인·로그아웃·부트스트랩·계정 삭제 완료는 성공 자체가 세션을 바꿀 수 있어 전용 생명주기를 유지한다. 정상 refresh는 같은 세션의 작업을 취소하지 않는다.
- 서버가 이미 처리한 저장·구매·업로드는 브라우저 취소로 롤백할 수 없다. 최종 파일 연결 요청의 응답을 잃었을 때 파일을 삭제하지 않으며, 불확실한 미연결 파일은 기존 서버 만료 정리에 맡긴다. 새 계정 자격으로 이전 파일을 삭제하지 않는다.
- SSE 연결 소유권·재연결과 Web Push 구독 보상은 단발 mutation과 수명이 다르므로 기존 보호를 유지한다. 알림의 domain key 무효화 계약도 유지한다.
- 공개·뷰어 독립인 공통코드·인기 검색어·공개 이모티콘 목록은 공유 캐시를 유지한다. 공개 URL이어도 개인 권한에 따라 달라지는 상세는 보호 대상이다.
- sessionGeneration은 브라우저 작업 경계이며 서버 자격·권한·DB 잠금을 대신하지 않는다. callback 내부 await 뒤 부수 효과는 제공된 assertion으로 직접 검사해야 한다.

## 대표 회귀 검증

아래 목록은 현재 저장소에 있는 자동화 테스트의 검증 범위다. 이 문서를 읽거나 수정한 시점에 해당 테스트를 다시 실행했다는 뜻은 아니며, 실제 실행 결과는 작업별로 따로 보고한다.

| 테스트 | 주요 검증 범위 |
| --- | --- |
| [useAuthScopedOperation.spec.ts](../../frontend/src/composables/__tests__/useAuthScopedOperation.spec.ts) | 확인 대기·중복 작업·이전 finally·새 pending·unmount·정상 토큰 회전 |
| [authScopedQuery.spec.ts](../../frontend/src/utils/__tests__/authScopedQuery.spec.ts) | 실제 Vue Query/Axios의 오프라인·재시도·계정 교체·동일 사용자 재로그인·hydration·signal 결합 |
| [useAppUserSettingsSync.spec.ts](../../frontend/src/features/user/settings/__tests__/useAppUserSettingsSync.spec.ts) | 사용자 신원·세대 변경 이후 오래된 설정과 지연 로케일 반영 차단 |
| [useAppUserSettingsSyncSession.spec.ts](../../frontend/src/features/user/settings/__tests__/useAppUserSettingsSyncSession.spec.ts) | 실제 auth store·Vue Query의 `/me` 대기, 캐시·계정 전환·지연 로케일, 정상 refresh와 동일 사용자 재조회 |
| [InquiryNewLifecycle.spec.ts](../../frontend/src/views/inquiry/__tests__/InquiryNewLifecycle.spec.ts) | 실제 Vue Query의 unmount 전후 지연 성공·실패, 자원 정리·목록 갱신 유지와 오래된 탐색 차단 |
| [session-ui-lifecycle.spec.ts](../../frontend/e2e/session-ui-lifecycle.spec.ts) | 실제 브라우저의 프로필 저장 중 Enter 재제출, 사용자 hydration 이후 설정 복원, 문의 작성 중 목록으로 이탈한 뒤 지연 완료 |
| [useProfileUpdateSubmitLifecycle.spec.ts](../../frontend/src/features/user/profile/__tests__/useProfileUpdateSubmitLifecycle.spec.ts) | 실제 프로필 form의 단계별 중복 제출 차단, 취소·실패 후 재시도, 세션 전환 후 새 작업의 pending 보존 |
| [authSessionScope.spec.ts](../../frontend/src/utils/__tests__/authSessionScope.spec.ts) | 세대·사용자 변경, 정상 토큰 회전, signal 결합, 오래된 수동 결과·오류, 리스너 해제 |
| [useAuthScopedMutation.spec.ts](../../frontend/src/composables/__tests__/useAuthScopedMutation.spec.ts) | 실제 Vue Query의 오프라인 대기, 비동기 onMutate, 재시도, 중첩 실행, unmount 이후 완료와 정리 |
| [apiExpectedSession.spec.ts](../../frontend/src/api/__tests__/apiExpectedSession.spec.ts) | 실제 Axios 인터셉터의 전송·응답·refresh queue·재전송 경계 |
| [apiRefreshCancellation.spec.ts](../../frontend/src/api/__tests__/apiRefreshCancellation.spec.ts) | 실제 Axios·공유 refresh에서 최초 요청·대기 요청 취소의 분리, 세션 변경, HTTP·hydration 실패 정책 |
| [commonCodeSession.spec.ts](../../frontend/src/api/__tests__/commonCodeSession.spec.ts) | 공통코드 API facade를 통과하는 config 전달과 계정 전환 차단 |
| [CommonCodeManagement.spec.ts](../../frontend/src/views/admin/__tests__/CommonCodeManagement.spec.ts) | 확인창, 재조회, 오래된 finally, 새 계정의 저장 상태 |
| [emoticonSessionCache.spec.ts](../../frontend/src/features/emoticon/detail/__tests__/emoticonSessionCache.spec.ts) | 실제 Vue Query의 로그아웃·계정 전환·지연 상세 응답·동일 사용자 재로그인과 구매/숨김/편집 갱신; 상세 이동·unmount 이후 성공한 변경의 캐시 갱신 |
| [useEmoticonEditForm.spec.ts](../../frontend/src/features/emoticon/form/__tests__/useEmoticonEditForm.spec.ts) | 편집 초기화와 세션 변경 전 확인창의 실행 차단 |
| [RefreshTokenCookieWriterTest](../../backend/src/test/java/com/weedrice/whiteboard/global/security/RefreshTokenCookieWriterTest.java) | refresh 쿠키 속성과 정리 |
| [SessionTokenServiceTest](../../backend/src/test/java/com/weedrice/whiteboard/domain/auth/service/SessionTokenServiceTest.java) | refresh·폐기·세션 family 동작 |
| [UserSessionServicePersistenceTest](../../backend/src/test/java/com/weedrice/whiteboard/domain/user/service/UserSessionServicePersistenceTest.java) | 실제 refresh 이후 이전 숫자 ID로 family 종료, 원행 만료, 다른 family 보존; H2 기반 |
| [PasswordResetServicePersistenceTest](../../backend/src/test/java/com/weedrice/whiteboard/domain/auth/service/PasswordResetServicePersistenceTest.java) | 후보 조회와 사용자 잠금 사이 별도 트랜잭션의 링크 소비 후 재사용 거부, 정책 실패 롤백; H2 기반이며 PostgreSQL 동시 스레드 검증은 아님 |

명령과 실행 조건은 [프런트 검증 표](../../frontend/AGENTS.md#verification)와 [백엔드 인증 검증](../../backend/src/main/java/com/weedrice/whiteboard/domain/auth/AGENTS.md#verification)을 따른다. 위 목록에서 변경 경로에 맞는 테스트를 고르고, 프런트 mock 성공을 백엔드 토큰 폐기·실제 쿠키·운영 SSE 연결 검증으로 보고하지 않는다.
