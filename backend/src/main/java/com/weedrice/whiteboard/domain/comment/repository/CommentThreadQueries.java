package com.weedrice.whiteboard.domain.comment.repository;

import com.weedrice.whiteboard.domain.actor.query.ContentAuthorVisibility;

final class CommentThreadQueries {

    // Hidden ancestors remain only as placeholders for eligible descendants.
    static final String VISIBLE_THREAD = """
              AND (
                    (c.isDeleted = false
            """ + CommentVisibilityJpql.VISIBLE_COMMENT_AUTHOR + """
                    )
                    OR EXISTS (
                            SELECT 1
                            FROM CommentClosure cc
                            JOIN cc.descendant descendant
                            WHERE cc.ancestor = c
                              AND cc.depth > 0
                              AND descendant.isDeleted = false
                              AND descendant.userId IN (
            """ + ContentAuthorVisibility.VISIBLE_AUTHOR_IDS_JPQL + """
                              )
                              AND (:blockedUserIdsEmpty = true
                                   OR descendant.userId NOT IN (:blockedUserIds))
                    )
              )
            """;

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
