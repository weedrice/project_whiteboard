package com.weedrice.whiteboard.domain.actor;

import java.time.LocalDateTime;

/** Public presentation only; never mutates retained account identity or ownership. */
public final class AuthorPresentation {
    public static final String WITHDRAWN_DISPLAY_NAME = "탈퇴한 사용자";

    private AuthorPresentation() {
    }

    public static boolean isWithdrawn(String status, LocalDateTime deletedAt) {
        return "DELETED".equals(status) || deletedAt != null;
    }

    public static String displayName(boolean withdrawn, String original) {
        return withdrawn ? WITHDRAWN_DISPLAY_NAME : original;
    }

    public static String profileImageUrl(boolean withdrawn, String original) {
        return withdrawn ? null : original;
    }
}
