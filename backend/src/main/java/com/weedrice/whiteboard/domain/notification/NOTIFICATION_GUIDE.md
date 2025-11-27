# Notification 도메인 가이드

`notification` 도메인은 사용자에게 발생하는 다양한 이벤트(새 댓글, 좋아요 등)에 대한 알림을 생성하고 관리하는 기능을 담당합니다.

## 1. 주요 기능 및 로직

`NotificationService`를 통해 다음과 같은 핵심 비즈니스 로직을 처리합니다.

- **알림 생성 (`handleNotificationEvent`):**
  - Spring의 `ApplicationEventPublisher`를 통해 발행된 `NotificationEvent`를 수신하여 동작합니다.
  - `@TransactionalEventListener`를 사용하여, 이벤트를 발생시킨 원래의 트랜잭션(예: 좋아요, 댓글 생성)이 성공적으로 커밋된 후에만 알림 생성 로직이 실행되도록 보장합니다.
  - 알림을 발생시킨 사용자와 알림을 받는 사용자가 동일한 경우(자기 자신의 글에 좋아요 등)에는 알림을 생성하지 않습니다.
  - 생성된 알림은 `notifications` 테이블에 저장됩니다.

- **알림 목록 조회 (`getNotifications`):**
  - 현재 로그인한 사용자의 알림 목록을 최신순으로 페이징하여 조회합니다.

- **알림 읽음 처리 (`readNotification`, `readAllNotifications`):**
  - 특정 알림을 개별적으로 읽음 처리하거나, 모든 알림을 한 번에 읽음 처리합니다.
  - `is_read` 플래그를 'Y'로 변경합니다.

- **읽지 않은 알림 수 조회 (`getUnreadNotificationCount`):**
  - 사용자의 읽지 않은 알림 개수를 조회합니다.

## 2. API Endpoints

`NotificationController`에서 다음 API를 제공합니다.

| Method | URI                                  | 설명                     |
| :----- | :----------------------------------- | :----------------------- |
| `GET`    | `/api/v1/notifications`              | 내 알림 목록 조회        |
| `PUT`    | `/api/v1/notifications/{notificationId}/read` | 특정 알림 읽음 처리      |
| `PUT`    | `/api/v1/notifications/read-all`     | 모든 알림 읽음 처리      |
| `GET`    | `/api/v1/notifications/unread-count` | 읽지 않은 알림 수 조회   |

## 3. 관련 DB 테이블

`notification` 도메인은 주로 다음 테이블과 상호작용합니다.

| 테이블명          | 엔티티          | 설명             |
| :---------------- | :-------------- | :--------------- |
| `notifications`   | `Notification`  | 알림 정보        |
| `users`           | `User`          | 알림 수신자 및 발생자 정보 |

이 문서는 `notification` 도메인의 기능을 이해하고 개발하는 데 도움을 주기 위해 작성되었습니다.
