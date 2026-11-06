package com.weedrice.whiteboard.domain.comment.repository;

import com.weedrice.whiteboard.domain.actor.query.ContentAuthorVisibility;

/** Eligibility shared by latest activity, unread totals, and newer-comment selection. */
final class AgentPostActivityVisibility {
    private AgentPostActivityVisibility() {
    }

    static final String SOURCE = """
            AND p.isBlinded = false
            AND b.isActive = true
            AND b.isPublic = true
            AND b.agentUseYn = true
            """;

    static final String COMMENT = " AND c.userId IN ("
            + ContentAuthorVisibility.VISIBLE_AUTHOR_IDS_JPQL + ") " + """
            AND c.isBlinded = false
            AND NOT EXISTS (
                SELECT 1 FROM UserBlock activityBlock
                WHERE (activityBlock.user.userId = p.userId AND activityBlock.target.userId = c.userId)
                   OR (activityBlock.target.userId = p.userId AND activityBlock.user.userId = c.userId)
            )
            """;

    static final String LATEST = " AND latest.userId IN ("
            + ContentAuthorVisibility.VISIBLE_AUTHOR_IDS_JPQL + ") " + """
            AND latest.isBlinded = false
            AND NOT EXISTS (
                SELECT 1 FROM UserBlock activityBlock
                WHERE (activityBlock.user.userId = p.userId AND activityBlock.target.userId = latest.userId)
                   OR (activityBlock.target.userId = p.userId AND activityBlock.user.userId = latest.userId)
            )
            """;

    static final String NEWER = " AND newer.userId IN ("
            + ContentAuthorVisibility.VISIBLE_AUTHOR_IDS_JPQL + ") " + """
            AND newer.isBlinded = false
            AND NOT EXISTS (
                SELECT 1 FROM UserBlock activityBlock
                WHERE (activityBlock.user.userId = p.userId AND activityBlock.target.userId = newer.userId)
                   OR (activityBlock.target.userId = p.userId AND activityBlock.user.userId = newer.userId)
            )
            """;
}
