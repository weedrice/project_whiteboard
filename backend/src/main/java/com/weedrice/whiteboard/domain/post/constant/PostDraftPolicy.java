package com.weedrice.whiteboard.domain.post.constant;

import java.util.regex.Pattern;

public final class PostDraftPolicy {

    public static final int RETENTION_DAYS = 90;
    public static final int MAX_DRAFTS_PER_USER = 100;
    public static final int MAX_CLIENT_DRAFT_KEY_LENGTH = 64;
    public static final String CLIENT_DRAFT_KEY_PATTERN = "[A-Za-z0-9_-]{8,64}";
    private static final Pattern CLIENT_DRAFT_KEY_PATTERN_MATCHER = Pattern.compile(CLIENT_DRAFT_KEY_PATTERN);

    public static boolean isValidClientDraftKey(String clientDraftKey) {
        return clientDraftKey != null
                && clientDraftKey.length() <= MAX_CLIENT_DRAFT_KEY_LENGTH
                && CLIENT_DRAFT_KEY_PATTERN_MATCHER.matcher(clientDraftKey).matches();
    }

    private PostDraftPolicy() {
    }
}
