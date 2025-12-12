# User 도메인 가이드

`user` 도메인은 회원 프로필, 보안(비밀번호 변경/탈퇴), 개인 설정, 차단, 내 활동 조회를 관리합니다.

## 1. 주요 기능 및 로직
- 프로필 조회/수정: 내 정보와 다른 사용자 프로필을 조회하고, 닉네임 변경 시 이력을 남기며 프로필 이미지 파일을 연결합니다.
- 비밀번호 관리: 현재 비밀번호 검증, 최근 3개 비밀번호 재사용 방지, 변경 이력 저장.
- 계정 탈퇴: 비밀번호 확인 후 계정을 비활성화 처리.
- 설정 관리: 테마/언어/시간대/NSFW 설정 및 알림 설정 CRUD.
- 차단 관리: 사용자 차단/해제, 차단 목록 조회(차단 시 자기 자신 차단 불가 검증).
- 내 활동: 구독 게시판, 내가 쓴 글/댓글, 최근 본 글(Page) 조회.
- 관리자 기능: 키워드로 사용자 검색 및 상태(ACTIVE/SUSPENDED) 변경.

## 2. API Endpoints

| Method | URI | 설명 |
| :----- | :------------------------------------------------ | :------------------------------- |
| `GET` | `/api/v1/users/me` | 내 기본 정보 조회 |
| `GET` | `/api/v1/users/{userId}` | 사용자 프로필 조회 |
| `PUT` | `/api/v1/users/me` | 프로필 수정(닉네임/프로필 이미지) |
| `PUT` | `/api/v1/users/me/password` | 비밀번호 변경 |
| `DELETE` | `/api/v1/users/me` | 계정 탈퇴 |
| `GET` | `/api/v1/users/me/settings` | 내 환경설정 조회 |
| `PUT` | `/api/v1/users/me/settings` | 내 환경설정 수정 |
| `GET` | `/api/v1/users/me/notification-settings` | 알림 설정 목록 |
| `PUT` | `/api/v1/users/me/notification-settings` | 알림 설정 수정 |
| `POST` | `/api/v1/users/{userId}/block` | 사용자 차단 |
| `DELETE` | `/api/v1/users/{userId}/block` | 사용자 차단 해제 |
| `GET` | `/api/v1/users/me/blocks` | 차단 목록 조회 |
| `GET` | `/api/v1/users/me/subscriptions` | 구독 게시판 목록 |
| `GET` | `/api/v1/users/me/posts` | 내가 쓴 글 목록 |
| `GET` | `/api/v1/users/me/comments` | 내가 쓴 댓글 목록 |
| `GET` | `/api/v1/users/me/history/views` | 최근 본 글 목록 |
| `GET` | `/api/v1/admin/users` | (관리자) 사용자 검색 |
| `PUT` | `/api/v1/admin/users/{userId}/status` | (관리자) 사용자 상태 변경 |

## 3. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :------- | :----- | :--- |
| `users` | `User` | 회원 기본 정보/상태 |
| `display_name_histories` | `DisplayNameHistory` | 닉네임 변경 이력 |
| `password_histories` | `PasswordHistory` | 최근 비밀번호 해시 이력 |
| `user_settings` | `UserSettings` | 테마/언어/시간대/NSFW 설정 |
| `user_notification_settings` | `UserNotificationSettings` | 알림 타입별 설정 |
| `user_blocks` | `UserBlock` | 사용자 차단 관계 |
