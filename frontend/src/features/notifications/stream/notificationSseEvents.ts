/**
 * 알림 SSE 스트림의 이벤트 이름 계약.
 *
 * 값은 백엔드 `com.weedrice.whiteboard.domain.notification.web.NotificationSseEvents`와
 * 1:1로 대응하며 한쪽을 고치면 반드시 다른 쪽도 함께 고쳐야 한다.
 *
 * 가드는 타입이 아니라 테스트가 맡는다. SSE 이벤트 이름은 wire에서 임의 문자열로 도착하므로
 * `handleSseEvent`의 파라미터는 `string`일 수밖에 없고, 따라서 분기 누락이나 오타를
 * 타입 검사로 잡을 수 없다. 대신 `notificationSseEvents.spec.ts`가
 * (1) 백엔드 상수 집합과의 일치, (2) 모든 이름이 실제로 분기 처리되는지를 검증한다.
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
    /** 관리자에 의한 상점 아이템 판매 상태 변경. */
    SHOP_ITEM_SALE_STATUS_CHANGED: 'shop-item-sale-status-changed',
} as const

/**
 * SSE 규격이 정한, `event:` 줄이 없는 프레임의 기본 이름.
 * 백엔드가 보내는 이름이 아니라 프로토콜 기본값이며 `notificationSseStream.ts`가 이 값을 채운다.
 * 백엔드가 이름 없는 data 프레임을 보내게 되더라도 알림으로 처리하기 위해 함께 받아 둔다.
 */
export const SSE_DEFAULT_EVENT_NAME = 'message'
