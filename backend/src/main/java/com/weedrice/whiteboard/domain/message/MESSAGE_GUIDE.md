# Message 도메인 가이드

`message` 도메인은 사용자 간 쪽지 송수신, 읽음/차단 처리, 알림 카운트를 관리합니다.

## 1. 주요 기능 및 로직
- 쪽지 발송: 발신/수신자 존재 및 상호 차단 여부를 검증 후 저장.
- 수신/발신함 조회: 차단한 사용자 메시지는 제외하고 페이지 단위로 제공.
- 쪽지 상세: 발신·수신자만 접근 가능하며, 수신자가 읽으면 읽음 처리.
- 삭제: 발신자/수신자별 소프트 삭제, 양쪽 모두 삭제 시 DB에서 완전 삭제.
- 안읽은 수: 차단 목록을 제외하고 안읽은 쪽지 개수 집계.

## 2. API Endpoints

| Method | URI | 설명 |
| :----- | :-------------------------------- | :--------------------- |
| `POST` | `/api/v1/messages` | 쪽지 발송 |
| `GET` | `/api/v1/messages/received` | 받은 쪽지 목록 |
| `GET` | `/api/v1/messages/sent` | 보낸 쪽지 목록 |
| `GET` | `/api/v1/messages/{messageId}` | 쪽지 상세 조회 |
| `DELETE` | `/api/v1/messages/{messageId}` | 쪽지 삭제 |
| `DELETE` | `/api/v1/messages` | 쪽지 일괄 삭제 |
| `GET` | `/api/v1/messages/unread-count` | 안읽은 쪽지 수 조회 |

## 3. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :------- | :----- | :--- |
| `messages` | `Message` | 쪽지 본문/상태(읽음·삭제) |
