# 프론트엔드 로직 개선 리포트
> 분석 일시: 20260529-170813

## 📋 요약
기존 문서는 BaseTable 전환, smoke 검증, 의존성 정비 중심이라 아래 항목과 중복되지 않는다.
신규 후보는 단축키 처리 중복, 날짜 응답 매핑 중복, 알림 이동의 DTO 결합도에 한정했다.
직접 HTTP 우회, v-for key 누락, 명백한 lifecycle cleanup 누락은 이번 분석 범위에서 신규 후보로 확인되지 않았다.

## 🔴 즉시 수정 필요
(권한/세션 우회, cleanup 누락, race condition, 식별자 오류 등)

| 파일 | 라인 | 문제 | 제안 |
|------|------|------|------|

## 🟡 리팩토링 권장
(컴포넌트 책임 분리, composable/store 정리, prop drilling 해소 등)

| 파일 | 라인 | 문제 | 제안 |
|------|------|------|------|
| frontend/src/composables/useKeyboardShortcuts.ts | 200-208 | [composable_extraction_needed] useBoardDetailShortcuts가 document keydown에서 `/`를 직접 처리하고, App.vue#onMounted도 useGlobalShortcuts에 같은 `/` 검색 포커스 단축키를 등록해 `/board/:boardUrl` 화면에서 두 shortcut handler가 같은 입력을 경쟁적으로 처리할 수 있다. | board 상세의 `/` 처리도 useGlobalShortcuts에 route-scoped override 또는 priority handler로 등록하고, shortcut 실행 결과를 handled 상태로 반환해 전역 검색 포커스와 보드 검색 포커스가 동시에 실행되지 않게 한다. |
| frontend/src/components/comment/CommentItem.vue | 60-115 | [duplicate_response_mapping] <script setup>에서 createdAtFull은 formatDate를 쓰지만 createdAtShort는 같은 createdAt 응답 필드를 new Date와 padStart로 직접 포맷해 utils/date.ts의 formatDateShort와 동일한 정규화 로직이 컴포넌트에 중복되어 있다. | formatDateShort를 frontend/src/utils/date.ts에서 import해 createdAtShort computed에 사용하고, CommentItem.vue의 로컬 formatCommentDateShort 함수를 제거한다. |

## 🟢 장기 개선 검토
(백엔드 응답 결합도, view-model 계층 신설, 타입 정비, 성능 최적화 등)

| 파일 | 라인 | 문제 | 제안 |
|------|------|------|------|
| frontend/src/composables/useNotificationNavigation.ts | 62-81 | [backend_response_coupling] navigateFromNotification가 notification.sourceType/sourceId만으로 라우트를 만들지 못해 postApi.getPost 또는 commentApi.getComment 응답의 boardUrl/postId 구조를 다시 읽으며, backend NotificationResponse가 sourceType/sourceId까지만 제공하는 DTO 형태에 알림 클릭 흐름이 강하게 결합되어 있다. | api/notification의 normalizeNotification에서 targetRoute 또는 targetUrl view-model을 만들고, 필요하면 backend NotificationResponse에 targetUrl을 추가한 뒤 navigateFromNotification은 해당 view-model을 우선 사용하고 post/comment 조회는 fallback으로 격리한다. |
