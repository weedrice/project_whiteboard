# Notification 도메인 가이드

`notification` 도메인은 도메인 이벤트 기반 알림 저장과 SSE 푸시, 읽음 상태 관리를 담당합니다.

## 1. 주요 기능 및 로직
- 알림 생성: 댓글/좋아요 등 `NotificationEvent`를 원 업무 트랜잭션에서 `notification_delivery_jobs`에 저장합니다. 커밋 후 worker가 알림 생성과 job 완료를 한 트랜잭션으로 처리하며, lease·지수 backoff·dead-letter로 일시 장애를 복구합니다. 자기 자신 대상은 worker 처리 시 무시합니다.
- Web Push: 알림 생성과 같은 트랜잭션에서 이벤트·구독별 `push_delivery_jobs`를 생성합니다. 외부 Push 전송은 전용 scheduler가 한 번에 최대 20건씩 DB 트랜잭션 밖에서 수행하며 timeout·429·5xx는 지수 backoff로 재시도하고 404·410은 해당 구독을 조건부 삭제합니다.
- Web Push 전달 보장은 at-least-once입니다. 공급자 전송 성공 직후 프로세스가 종료되면 같은 작업이 다시 전송될 수 있으므로 payload의 안정적인 `notification-{notificationId}` tag로 현재 표시 중인 알림을 교체합니다. 사용자가 알림을 닫았거나 Service Worker가 재시작된 뒤의 재전송까지 정확히 한 번으로 만들지는 않습니다.
- SSE 구독: 사용자별 `SseEmitter`를 등록해 실시간 알림을 전송하며, 연결 상태 이벤트(`connect`)를 즉시 송신합니다.
- 댓글 topic 권한: 구독은 보드 행 잠금 아래에서 게시글 읽기 권한과 함께 등록합니다. 보드 공개·활성 상태 또는 관리자가 바뀌면 commit 후 topic을 폐기하고 영향받은 SSE 연결을 재연결시켜 새 권한으로 다시 구독하게 합니다.
- 댓글 topic은 읽기 권한 확인 후 게시글과 보드에 연결하며, 보드 비공개·비활성 또는 관리자 변경이 커밋되면 해당 보드의 topic을 폐기합니다.
- 조회/읽음 처리: 알림 목록 페이지 조회, 단건 읽음 처리, 전체 읽음 처리 기능 제공.
- 미읽음 집계: 사용자별 읽지 않은 알림 건수를 반환합니다.
- 키워드 구독: 사용자가 알림 키워드 목록을 조회·등록·해지하며 게시글 생성 시 durable fan-out 작업으로 대상 알림을 생성합니다.
- Web Push: 사용자별 브라우저 구독을 등록·해지하며, 전체 해지는 사용자 잠금 아래 모든 구독 삭제와 push 설정 비활성화를 원자적으로 처리합니다.
- 비동기 포화: 커밋 후 즉시 kickoff/push 제출이 거부돼도 이미 성공한 업무 트랜잭션에는 예외를 전파하지 않습니다. durable delivery job은 scheduler가 다시 선택합니다.

## 2. API Endpoints

| Method | URI | 설명 |
| :----- | :-------------------------------- | :--------------------------- |
| `GET` | `/api/v1/notifications` | 알림 목록 조회 |
| `PUT` | `/api/v1/notifications/{notificationId}/read` | 알림 단건 읽음 처리 |
| `PUT` | `/api/v1/notifications/read-all` | 알림 전체 읽음 처리 |
| `GET` | `/api/v1/notifications/unread-count` | 미읽음 알림 수 조회 |
| `GET` | `/api/v1/notifications/stream` | SSE 알림 스트림 구독 |
| `POST` | `/api/v1/notifications/comment-topics/{postId}/subscriptions` | 실시간 댓글 topic 구독 |
| `DELETE` | `/api/v1/notifications/comment-topics/{postId}/subscriptions/{subscriberId}` | 실시간 댓글 topic 구독 해지 |
| `GET` | `/api/v1/push/public-key` | Web Push VAPID 공개키 조회 |
| `GET` | `/api/v1/users/me/keyword-subscriptions` | 내 키워드 구독 목록 조회 |
| `POST` | `/api/v1/users/me/keyword-subscriptions` | 키워드 구독 등록 |
| `DELETE` | `/api/v1/users/me/keyword-subscriptions` | 요청 본문의 키워드로 구독 해지 |
| `POST` | `/api/v1/users/me/push-subscriptions` | Web Push 구독 등록 |
| `DELETE` | `/api/v1/users/me/push-subscriptions` | endpoint 기준 Web Push 구독 해지 |
| `DELETE` | `/api/v1/users/me/push-subscriptions/all` | Web Push 구독 전체 해지 |

## 3. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :------- | :----- | :--- |
| `notifications` | `Notification` | 알림 대상/행위자/본문/읽음 여부 |
| `notification_delivery_jobs` | `NotificationDeliveryJob` | 원 트랜잭션에서 저장되는 알림 전달 작업과 lease·재시도·dead-letter 상태 |
| `push_delivery_jobs` | `PushDeliveryJob` | 알림 이벤트·구독별 Web Push 전송 lease·재시도·만료·dead-letter 상태 |

완료·만료 작업은 7일, 최종 실패 작업은 장애 분석을 위해 30일 보존한 뒤 정리합니다.
