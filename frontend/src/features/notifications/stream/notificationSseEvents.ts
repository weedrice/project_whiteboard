/**
 * 알림 SSE 스트림의 이벤트 이름 계약.
 *
 * 값은 백엔드 `com.weedrice.whiteboard.domain.notification.web.NotificationSseEvents`와
 * 1:1로 대응한다. 백엔드는 `NotificationSseEventContractTest`가 상수 사용을 강제하고,
 * 프론트엔드는 이 union 타입이 분기 누락과 오타를 타입 검사에서 잡는다.
 * 한쪽을 고치면 반드시 다른 쪽도 함께 고쳐야 한다.
 */
export const NOTIFICATION_SSE_EVENTS = {
    /** 구독 직후 연결 식별자를 전달한다. */
    CONNECT: 'connect',
    /** 새 알림 한 건. */
    NOTIFICATION: 'notification',
    /** 구독 중인 게시글의 댓글 변경. */
    COMMENT: 'comment',
    /** 스페이스 단위 댓글 구독 무효화. */
    COMMENT_TOPIC_INVALIDATED: 'comment-topic-invalidated',
    /** 댓글 구독 대상 접근 권한 상실. */
    COMMENT_TOPIC_ACCESS_REVOKED: 'comment-topic-access-revoked',
} as const

export type NotificationSseEventName =
    (typeof NOTIFICATION_SSE_EVENTS)[keyof typeof NOTIFICATION_SSE_EVENTS]

/**
 * SSE 규격이 정한, `event:` 줄이 없는 프레임의 기본 이름.
 * 백엔드가 보내는 이름이 아니라 프로토콜 기본값이며 `notificationSseStream.ts`가 이 값을 채운다.
 * 백엔드가 이름 없는 data 프레임을 보내게 되더라도 알림으로 처리하기 위해 함께 받아 둔다.
 */
export const SSE_DEFAULT_EVENT_NAME = 'message'
