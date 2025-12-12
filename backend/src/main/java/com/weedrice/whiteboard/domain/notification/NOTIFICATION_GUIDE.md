# Notification 도메인 가이드

`notification` 도메인은 도메인 이벤트 기반 알림 저장과 SSE 푸시, 읽음 상태 관리를 담당합니다.

## 1. 주요 기능 및 로직
- 알림 생성: 댓글/좋아요 등 `NotificationEvent` 수신 시 새 알림을 저장하고, 자기 자신 대상이면 무시합니다.
- SSE 구독: 사용자별 `SseEmitter`를 등록해 실시간 알림을 전송하며, 연결 상태 이벤트(`connect`)를 즉시 송신합니다.
- 조회/읽음 처리: 알림 목록 페이지 조회, 단건 읽음 처리, 전체 읽음 처리 기능 제공.
- 미읽음 집계: 사용자별 읽지 않은 알림 건수를 반환합니다.

## 2. API Endpoints

| Method | URI | 설명 |
| :----- | :-------------------------------- | :--------------------------- |
| `GET` | `/api/v1/notifications` | 알림 목록 조회 |
| `PUT` | `/api/v1/notifications/{notificationId}/read` | 알림 단건 읽음 처리 |
| `PUT` | `/api/v1/notifications/read-all` | 알림 전체 읽음 처리 |
| `GET` | `/api/v1/notifications/unread-count` | 미읽음 알림 수 조회 |
| `GET` | `/api/v1/notifications/stream` | SSE 알림 스트림 구독 |

## 3. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :------- | :----- | :--- |
| `notifications` | `Notification` | 알림 대상/행위자/본문/읽음 여부 |
