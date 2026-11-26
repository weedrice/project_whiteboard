package com.weedrice.whiteboard.domain.comment.repository;

import com.weedrice.whiteboard.domain.actor.query.ContentAuthorVisibility;

final class CommentThreadQueries {

    // A qualifying descendant keeps a hidden ancestor visible and contributes to its reply count.
    private static final String HAS_VISIBLE_DESCENDANT = """
            EXISTS (
                SELECT 1
                FROM CommentClosure cc
                JOIN cc.descendant descendant
                WHERE cc.ancestor = c
                  AND cc.depth > 0
                  AND descendant.isDeleted = false
                  AND descendant.userId IN (
            """ + ContentAuthorVisibility.VISIBLE_AUTHOR_IDS_JPQL + """
                  )
                  AND (:blockedUserIdsEmpty = true OR descendant.userId NOT IN (:blockedUserIds))
            )
            """;

    private static final String ACTIVE_WITH_VISIBLE_AUTHOR = "c.isDeleted = false " + CommentVisibilityJpql.VISIBLE_COMMENT_AUTHOR;
    static final String VISIBLE_THREAD = " AND ((" + ACTIVE_WITH_VISIBLE_AUTHOR + ") OR " + HAS_VISIBLE_DESCENDANT + ") ";
    static final String COUNTED_REPLY = " AND ((" + ACTIVE_WITH_VISIBLE_AUTHOR
            + " AND (:blockedUserIdsEmpty = true OR c.userId NOT IN (:blockedUserIds))) OR "
            + HAS_VISIBLE_DESCENDANT + ") ";

    private static final String ROOT_FROM = """
            FROM Comment c
            WHERE c.postId = :postId
              AND c.parent IS NULL
            """ + VISIBLE_THREAD;

    static final String ROOT_CONTENT = "SELECT DISTINCT c " + ROOT_FROM;
    static final String ROOT_COUNT = "SELECT COUNT(DISTINCT c) " + ROOT_FROM;
    static final String OLDEST_ORDER = " ORDER BY c.createdAt ASC, c.commentId ASC";
    static final String NEWEST_ORDER = " ORDER BY c.createdAt DESC, c.commentId DESC";
    static final String LIKE_ORDER = " ORDER BY c.likeCount DESC, c.createdAt ASC, c.commentId ASC";

    private CommentThreadQueries() {
    }
}
