package com.weedrice.whiteboard.domain.comment.repository;

final class CommentThreadQueries {

    static final String ROOT_CONTENT = """
            SELECT DISTINCT c
            FROM Comment c
            JOIN FETCH c.user
            LEFT JOIN FETCH c.agent
            JOIN FETCH c.post p
            JOIN FETCH p.board
            WHERE c.post.postId = :postId
              AND c.parent IS NULL
              AND (
                    c.isDeleted = false
                    OR EXISTS (
                            SELECT 1
                            FROM CommentClosure cc
                            JOIN cc.descendant descendant
                            WHERE cc.ancestor = c
                              AND cc.depth > 0
                              AND descendant.isDeleted = false
                              AND (:blockedUserIdsEmpty = true
                                   OR descendant.user.userId NOT IN (:blockedUserIds))
                    )
              )
            """;

    static final String ROOT_COUNT = """
            SELECT COUNT(DISTINCT c)
            FROM Comment c
            WHERE c.post.postId = :postId
              AND c.parent IS NULL
              AND (
                    c.isDeleted = false
                    OR EXISTS (
                            SELECT 1
                            FROM CommentClosure cc
                            JOIN cc.descendant descendant
                            WHERE cc.ancestor = c
                              AND cc.depth > 0
                              AND descendant.isDeleted = false
                              AND (:blockedUserIdsEmpty = true
                                   OR descendant.user.userId NOT IN (:blockedUserIds))
                    )
              )
            """;

    static final String OLDEST_ORDER = " ORDER BY c.createdAt ASC, c.commentId ASC";
    static final String NEWEST_ORDER = " ORDER BY c.createdAt DESC, c.commentId DESC";
    static final String LIKE_ORDER = " ORDER BY c.likeCount DESC, c.createdAt ASC, c.commentId ASC";

    private CommentThreadQueries() {
    }
}
