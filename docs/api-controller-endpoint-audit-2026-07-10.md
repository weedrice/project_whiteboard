# API 컨트롤러-명세 대조표 (2026-07-10)

## 감사 기준

- 구현 기준: `backend/src/main/java/com/weedrice/whiteboard/**/*Controller.java`
- 비교 대상: `backend/API명세서.md` 기준일 2026-05-29
- 판정 원칙: URI와 HTTP method는 컨트롤러 매핑을 최종 기준으로 한다.
- 범위: 기존 명세 기준일 이후 추가되거나 변경된 컨트롤러 매핑을 우선 확인하고, 전체 컨트롤러가 명세 카탈로그에 포함되는지도 함께 점검한다.

## 컨트롤러 단위 대조

| 영역 | Controller | 2026-05-29 명세 대비 | 조치 |
| --- | --- | --- | --- |
| 광고 | `AdController` | 누락 없음 | 유지 |
| 관리자 | `AdminController` | 심화 통계 1건 누락 | `GET /api/v1/admin/stats/deep` 추가 |
| Agent | `AgentController` | 누락 없음 | 유지 |
| 출석 | `AttendanceController` | 컨트롤러 전체 누락 | 출석 체크와 월별 현황 2건 추가 |
| 인증 | `AuthController` | 누락 없음 | 유지 |
| 뱃지 | `BadgeController`, `BadgeAdminController` | 컨트롤러 전체 누락 | 사용자 뱃지 3건, backfill 1건 추가 |
| 스페이스 | `BoardController` | 추천과 최근 갱신 2건 누락 | 2건 추가 |
| 댓글 | `CommentController` | 베스트 댓글 1건 누락 | 1건 추가, 목록 정렬 파라미터 명시 |
| 공통 코드 | `CommonCodeController` | 누락 없음 | 유지 |
| 이모티콘 | `EmoticonController` | 누락 없음 | 유지 |
| 피드/홈 | `FeedController`, `HomeController` | 누락 없음 | 유지 |
| 파일 | `FileController`, `LegacyFileController` | 이미지 variant 1건 누락 | 1건 추가 |
| 쪽지 | `MessageController` | 대화 목록/상세 2건 누락 | 2건 추가 |
| 감사 로그 | `AdminModerationAuditController`, `BoardManagerModerationAuditController` | 컨트롤러 전체 누락 | 전역/스페이스 감사 조회 2건 추가 |
| 알림 | `NotificationController` | 실시간 댓글 구독 2건 누락 | 댓글 topic 구독/해지 추가 |
| 키워드 알림 | `KeywordSubscriptionController` | 컨트롤러 전체 누락 | 조회/등록/해지 3건 추가 |
| 푸시 | `PushSubscriptionController`, `PushPublicKeyController` | 컨트롤러 전체 누락 | 구독 등록/해지와 공개키 3건 추가 |
| 포인트 | `PointController` | 누락 없음 | 유지 |
| 게시글 | `PostController` | 관련 글, 투표, 스크랩 폴더, 시리즈, 매니저 도구 13건 누락 | 13건 추가 |
| 예약 발행 | `ScheduledPostController` | 컨트롤러 전체 누락 | 생성/목록/상세/수정/취소 5건 추가 |
| 신고 | `ReportController`, `AdminReportController` | 누락 없음 | 유지 |
| 스페이스 신고 관리 | `BoardManagerReportController` | 컨트롤러 전체 누락 | 1건 추가 |
| 제재 | `SanctionController` | 누락 없음 | 유지 |
| 검색 | `SearchController`, `AdminSemanticSearchController` | 누락 없음 | 유지 |
| 상점 | `ShopController` | 누락 없음 | 유지 |
| 태그 | `TagController` | 추천 1건 누락, path variable 명칭 불일치 | 추천 추가, `{tagId}`를 `{tagKey}`로 정정 |
| 사용자 | `UserController` | 공개 활동, 멘션, 세션, 로그인 이력, 온보딩 8건 누락 | 8건 추가 |
| 관리자 사용자 | `AdminUserController` | 누락 없음 | 유지 |
| 전역 설정 | `GlobalConfigController` | 누락 없음 | 유지 |
| 에러 로그 | `ErrorLogController` | 관리자 조회 누락 없음 | 유지 |
| 감사 로그 | `LogController` | 경로는 있으나 필터 설명 부족 | 조회 필터 설명 보강 |
| CSP | `CspReportController` | 1건 누락 | CSP report 수집 추가 |
| 클라이언트 로그 | `ClientErrorLogController` | B2 신규 | `POST /api/v1/logs/client` 추가 |

## 누락 엔드포인트 상세

### 사용자와 참여

| Method | URI | Controller |
| --- | --- | --- |
| `GET` | `/api/v1/users/mention-candidates` | `UserController` |
| `GET` | `/api/v1/users/me/sessions` | `UserController` |
| `DELETE` | `/api/v1/users/me/sessions/{sessionId}` | `UserController` |
| `DELETE` | `/api/v1/users/me/sessions` | `UserController` |
| `GET` | `/api/v1/users/me/login-history` | `UserController` |
| `PUT` | `/api/v1/users/me/onboarding-complete` | `UserController` |
| `GET` | `/api/v1/users/{userId}/posts` | `UserController` |
| `GET` | `/api/v1/users/{userId}/comments` | `UserController` |
| `POST` | `/api/v1/attendance/check-in` | `AttendanceController` |
| `GET` | `/api/v1/attendance/me` | `AttendanceController` |
| `GET` | `/api/v1/users/{userId}/badges` | `BadgeController` |
| `GET` | `/api/v1/users/me/badges` | `BadgeController` |
| `PUT` | `/api/v1/users/me/badges/representative` | `BadgeController` |
| `POST` | `/api/v1/admin/badges/backfill` | `BadgeAdminController` |

### 게시글, 댓글, 탐색

| Method | URI | Controller |
| --- | --- | --- |
| `GET` | `/api/v1/boards/recommendations` | `BoardController` |
| `GET` | `/api/v1/boards/recent-updates` | `BoardController` |
| `GET` | `/api/v1/posts/{postId}/related` | `PostController` |
| `POST` | `/api/v1/posts/{postId}/manager/blind` | `PostController` |
| `DELETE` | `/api/v1/posts/{postId}/manager/blind` | `PostController` |
| `POST` | `/api/v1/posts/{postId}/poll/vote` | `PostController` |
| `DELETE` | `/api/v1/posts/{postId}/poll/vote` | `PostController` |
| `GET` | `/api/v1/users/me/scrap-folders` | `PostController` |
| `POST` | `/api/v1/users/me/scrap-folders` | `PostController` |
| `PATCH` | `/api/v1/users/me/scrap-folders/{folderId}` | `PostController` |
| `DELETE` | `/api/v1/users/me/scrap-folders/{folderId}` | `PostController` |
| `GET` | `/api/v1/users/me/post-series` | `PostController` |
| `POST` | `/api/v1/users/me/post-series` | `PostController` |
| `PATCH` | `/api/v1/users/me/post-series/{seriesId}` | `PostController` |
| `DELETE` | `/api/v1/users/me/post-series/{seriesId}` | `PostController` |
| `GET` | `/api/v1/posts/{postId}/comments/best` | `CommentController` |
| `POST` | `/api/v1/tags/suggestions` | `TagController` |
| `GET` | `/api/v1/files/{fileId}/variants/{variantType}` | `FileController` |

### 예약 발행

| Method | URI | Controller |
| --- | --- | --- |
| `POST` | `/api/v1/boards/{boardUrl}/scheduled-posts` | `ScheduledPostController` |
| `GET` | `/api/v1/users/me/scheduled-posts` | `ScheduledPostController` |
| `GET` | `/api/v1/scheduled-posts/{scheduledPostId}` | `ScheduledPostController` |
| `PUT` | `/api/v1/scheduled-posts/{scheduledPostId}` | `ScheduledPostController` |
| `DELETE` | `/api/v1/scheduled-posts/{scheduledPostId}` | `ScheduledPostController` |

### 알림과 쪽지

| Method | URI | Controller |
| --- | --- | --- |
| `POST` | `/api/v1/notifications/comment-topics/{postId}/subscriptions` | `NotificationController` |
| `DELETE` | `/api/v1/notifications/comment-topics/{postId}/subscriptions/{subscriberId}` | `NotificationController` |
| `GET` | `/api/v1/users/me/keyword-subscriptions` | `KeywordSubscriptionController` |
| `POST` | `/api/v1/users/me/keyword-subscriptions` | `KeywordSubscriptionController` |
| `DELETE` | `/api/v1/users/me/keyword-subscriptions` | `KeywordSubscriptionController` |
| `POST` | `/api/v1/users/me/push-subscriptions` | `PushSubscriptionController` |
| `DELETE` | `/api/v1/users/me/push-subscriptions` | `PushSubscriptionController` |
| `GET` | `/api/v1/push/public-key` | `PushPublicKeyController` |
| `GET` | `/api/v1/messages/conversations` | `MessageController` |
| `GET` | `/api/v1/messages/conversations/{partnerId}` | `MessageController` |

### 운영과 관리

| Method | URI | Controller |
| --- | --- | --- |
| `GET` | `/api/v1/boards/{boardUrl}/manager/reports` | `BoardManagerReportController` |
| `GET` | `/api/v1/boards/{boardUrl}/manager/audits` | `BoardManagerModerationAuditController` |
| `GET` | `/api/v1/admin/moderation-audits` | `AdminModerationAuditController` |
| `GET` | `/api/v1/admin/stats/deep` | `AdminController` |
| `POST` | `/api/v1/security/csp-report` | `CspReportController` |
| `POST` | `/api/v1/logs/client` | `ClientErrorLogController` |

## 확인 사항

- `DELETE /api/v1/users/me/keyword-subscriptions`는 `subscriptionId` path가 아니라 요청 body를 사용하는 현재 컨트롤러를 기준으로 기록했다.
- 댓글 실시간 갱신은 별도 댓글 SSE endpoint가 아니라 기존 `/api/v1/notifications/stream`과 댓글 topic 구독 API를 조합한다.
- 자동 블라인드와 프로필 이미지 포인트 차감은 기존 쓰기 API 내부 정책이므로 신규 endpoint가 없다.
- 클라이언트 오류 수집 요청은 필드별 길이 검증과 JSON body 32 KiB 상한을 함께 적용한다.
- 이 대조표 반영 후 `backend/API명세서.md`를 다시 검색해 위 URI가 모두 포함되는지 검증한다.
