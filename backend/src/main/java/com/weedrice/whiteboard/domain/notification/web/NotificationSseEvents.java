package com.weedrice.whiteboard.domain.notification.web;

import java.util.Set;

/**
 * 알림 SSE 스트림이 내보내는 이벤트 이름의 단일 출처.
 *
 * <p>이름은 프론트엔드 {@code notificationStreamController.ts}의 분기와 1:1로 대응하는
 * 계약이다. REST 응답과 달리 SSE는 envelope 검증 장치가 없으므로, 여기 상수를 거치지 않은
 * 이름을 쓰면 {@code NotificationSseEventContractTest}가 실패한다.
 *
 * <p>이름을 추가·변경할 때는 프론트엔드 union 타입도 함께 고쳐야 한다.
 */
public final class NotificationSseEvents {

    /** 구독 직후 연결 식별자를 전달한다. */
    public static final String CONNECT = "connect";

    /** 새 알림 한 건. */
    public static final String NOTIFICATION = "notification";

    /** 구독 중인 게시글의 댓글 변경. */
    public static final String COMMENT = "comment";

    /** 스페이스 단위로 댓글 구독을 무효화한다. */
    public static final String COMMENT_TOPIC_INVALIDATED = "comment-topic-invalidated";

    /** 댓글 구독 대상에 대한 접근 권한이 사라졌다. */
    public static final String COMMENT_TOPIC_ACCESS_REVOKED = "comment-topic-access-revoked";

    public static final Set<String> ALL = Set.of(
            CONNECT,
            NOTIFICATION,
            COMMENT,
            COMMENT_TOPIC_INVALIDATED,
            COMMENT_TOPIC_ACCESS_REVOKED);

    private NotificationSseEvents() {
    }
}
