# User 도메인 가이드

`user` 도메인은 인증을 제외한 사용자 정보와 관련된 모든 기능을 담당합니다. (프로필, 설정, 차단, 구독, 활동 내역 등)

## 1. 주요 기능 및 로직

`UserService`, `UserSettingsService`, `UserBlockService`를 통해 다음과 같은 핵심 비즈니스 로직을 처리합니다.

- **내 정보 조회 (`getMyInfo`):**
  - 현재 인증된 사용자의 전체 정보를 조회합니다.

- **사용자 프로필 조회 (`getUserProfile`):**
  - 특정 사용자의 공개 프로필 정보(게시글 수, 댓글 수 포함)를 조회합니다.

- **프로필 수정 (`updateMyProfile`):**
  - 닉네임 또는 프로필 이미지를 수정합니다.
  - 닉네임 변경 시, `display_name_histories` 테이블에 변경 이력을 기록합니다.

- **비밀번호 변경 (`updatePassword`):**
  - 현재 비밀번호를 확인한 후, 새로운 비밀번호로 변경합니다.
  - 보안을 위해 최근 사용했던 비밀번호로는 변경할 수 없습니다.
  - `password_histories` 테이블에 변경 이력을 기록합니다.

- **회원 탈퇴 (`deleteAccount`):**
  - 비밀번호를 확인한 후, 사용자를 '탈퇴(DELETED)' 상태로 변경합니다. (Soft Delete)

- **사용자 설정 관리 (`getSettings`, `updateSettings`):**
  - 사용자의 테마, 언어, NSFW 콘텐츠 표시 여부 등을 관리합니다.

- **알림 설정 관리 (`getNotificationSettings`, `updateNotificationSetting`):**
  - 각 알림 타입(댓글, 좋아요 등)에 대한 수신 여부를 관리합니다.

- **사용자 차단/해제 (`blockUser`, `unblockUser`):**
  - 특정 사용자를 차단하거나 차단을 해제합니다.

- **내 활동 내역 조회 (`getMyPosts`, `getMyComments`, `getMyScraps` 등):**
  - 내가 작성한 게시글, 댓글, 스크랩한 게시글 목록을 페이징하여 조회합니다.

## 2. API Endpoints

`UserController`에서 다음 API를 제공합니다.

| Method | URI                                | 설명                       |
| :----- | :--------------------------------- | :------------------------- |
| `GET`    | `/api/v1/users/me`                 | 내 정보 조회               |
| `GET`    | `/api/v1/users/{userId}`           | 특정 사용자 프로필 조회    |
| `PUT`    | `/api/v1/users/me`                 | 내 프로필 수정             |
| `PUT`    | `/api/v1/users/me/password`        | 비밀번호 변경              |
| `DELETE` | `/api/v1/users/me`                 | 회원 탈퇴                  |
| `GET`    | `/api/v1/users/me/settings`        | 내 설정 조회               |
| `PUT`    | `/api/v1/users/me/settings`        | 내 설정 수정               |
| `GET`    | `/api/v1/users/me/notification-settings` | 내 알림 설정 조회          |
| `PUT`    | `/api/v1/users/me/notification-settings` | 내 알림 설정 수정          |
| `POST`   | `/api/v1/users/{userId}/block`     | 사용자 차단                |
| `DELETE` | `/api/v1/users/{userId}/block`     | 사용자 차단 해제           |
| `GET`    | `/api/v1/users/me/blocks`          | 내 차단 목록 조회          |
| `GET`    | `/api/v1/users/me/subscriptions`   | 내 구독 게시판 목록 조회   |
| `GET`    | `/api/v1/users/me/posts`           | 내가 작성한 게시글 목록 조회 |
| `GET`    | `/api/v1/users/me/comments`        | 내가 작성한 댓글 목록 조회 |
| `GET`    | `/api/v1/users/me/scraps`          | 내 스크랩 목록 조회        |
| `GET`    | `/api/v1/users/me/drafts`          | 내 임시저장 글 목록 조회   |

### 2.1 Admin API (`AdminUserController`)

관리자 전용 기능을 제공합니다. (`SUPER` 권한 필요)

| Method | URI                        | 설명             |
| :----- | :------------------------- | :--------------- |
| `GET`    | `/api/v1/admin/users`      | 사용자 검색/조회 |

## 3. 관련 DB 테이블

`user` 도메인은 주로 다음 테이블과 상호작용합니다.

| 테이블명                       | 엔티티                         | 설명                           |
| :----------------------------- | :----------------------------- | :----------------------------- |
| `users`                        | `User`                         | 사용자 기본 정보               |
| `user_settings`                | `UserSettings`                 | 사용자 개인 설정               |
| `user_notification_settings`   | `UserNotificationSettings`     | 사용자 알림 수신 설정          |
| `user_blocks`                  | `UserBlock`                    | 사용자 차단 정보               |
| `display_name_histories`       | `DisplayNameHistory`           | 닉네임 변경 이력               |
| `password_histories`           | `PasswordHistory`              | 비밀번호 변경 이력             |
| `board_subscriptions`          | `BoardSubscription`            | 게시판 구독 정보               |

이 문서는 `user` 도메인의 기능을 이해하고 개발하는 데 도움을 주기 위해 작성되었습니다.
