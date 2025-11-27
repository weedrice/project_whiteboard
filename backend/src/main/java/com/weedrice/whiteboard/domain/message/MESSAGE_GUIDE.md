# Message (쪽지) 도메인 가이드

`message` 도메인은 사용자 간에 일대일로 메시지를 주고받는 **쪽지** 기능을 담당합니다.

## 1. 주요 기능 및 로직

`MessageService`를 통해 다음과 같은 핵심 비즈니스 로직을 처리합니다.

- **쪽지 발송 (`sendMessage`):**
  - 발신자와 수신자를 지정하여 쪽지를 보냅니다.
  - 수신자가 발신자를 차단했는지 여부를 확인하여, 차단된 경우 쪽지 발송을 막습니다.
  - 발송된 쪽지는 `messages` 테이블에 저장됩니다.

- **받은/보낸 쪽지 목록 조회 (`getReceivedMessages`, `getSentMessages`):**
  - 현재 로그인한 사용자를 기준으로 받은 쪽지함과 보낸 쪽지함을 페이징하여 조회합니다.
  - 각 사용자의 삭제 여부(`is_deleted_by_receiver`, `is_deleted_by_sender`)를 확인하여, 삭제하지 않은 쪽지만 보여줍니다.

- **쪽지 상세 조회 (`getMessage`):**
  - 특정 쪽지의 상세 내용을 조회합니다.
  - 쪽지의 발신자 또는 수신자만 조회할 수 있습니다.
  - 수신자가 쪽지를 조회하면, 해당 쪽지는 '읽음'(`is_read = 'Y'`) 상태로 변경됩니다.

- **쪽지 삭제 (`deleteMessage`):**
  - 쪽지를 받은 사람과 보낸 사람 각자의 입장에서 쪽지를 삭제합니다. (Soft Delete)
  - 만약 양쪽 모두 쪽지를 삭제하면, `messages` 테이블에서 해당 데이터가 완전히 삭제됩니다. (Hard Delete)

- **읽지 않은 쪽지 수 조회 (`getUnreadMessageCount`):**
  - 현재 로그인한 사용자의 읽지 않은 쪽지 개수를 조회합니다.

## 2. API Endpoints

`MessageController`에서 다음 API를 제공합니다.

| Method | URI                         | 설명                 |
| :----- | :-------------------------- | :------------------- |
| `POST`   | `/api/v1/messages`          | 쪽지 발송            |
| `GET`    | `/api/v1/messages/received` | 받은 쪽지 목록 조회  |
| `GET`    | `/api/v1/messages/sent`     | 보낸 쪽지 목록 조회  |
| `GET`    | `/api/v1/messages/{messageId}`| 쪽지 상세 조회       |
| `DELETE` | `/api/v1/messages/{messageId}`| 쪽지 삭제            |
| `GET`    | `/api/v1/messages/unread-count`| 읽지 않은 쪽지 수 조회 |

## 3. 관련 DB 테이블

`message` 도메인은 주로 다음 테이블과 상호작용합니다.

| 테이블명    | 엔티티    | 설명       |
| :---------- | :-------- | :--------- |
| `messages`  | `Message` | 쪽지 정보  |
| `users`     | `User`    | 발신/수신자 정보 |
| `user_blocks`| `UserBlock`| 사용자 차단 정보 확인 |

이 문서는 `message` (쪽지) 도메인의 기능을 이해하고 개발하는 데 도움을 주기 위해 작성되었습니다.
