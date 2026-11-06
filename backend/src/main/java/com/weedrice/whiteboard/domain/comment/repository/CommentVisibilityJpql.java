package com.weedrice.whiteboard.domain.comment.repository;

import com.weedrice.whiteboard.domain.actor.query.ContentAuthorVisibility;

final class CommentVisibilityJpql {

    private CommentVisibilityJpql() {
    }

    static final String VISIBLE_COMMENT_AUTHOR = " AND c.userId IN ("
            + ContentAuthorVisibility.VISIBLE_AUTHOR_IDS_JPQL + ") ";
    static final String VISIBLE_AUTHORS = VISIBLE_COMMENT_AUTHOR + " AND p.userId IN ("
            + ContentAuthorVisibility.VISIBLE_AUTHOR_IDS_JPQL + ") ";

    static final String VIEWER_READABLE_POST = """
              AND (
                    LOWER(b.boardUrl) <> :inquiryBoardUrl
                    OR :legacyInquiryUserAccessEnabled = true
                    OR :viewerIsSuperAdmin = true
                  )
              AND (
                    p.isBlinded = false
                    OR p.userId = :userId
                    OR :viewerIsSuperAdmin = true
                    OR EXISTS (
                        SELECT 1 FROM Admin a
                        WHERE a.user.userId = :userId AND a.board = b AND a.isActive = true
                    )
                  )
              AND (
                    b.isActive = true
                    OR p.userId = :userId
                    OR :viewerIsSuperAdmin = true
                    OR EXISTS (
                        SELECT 1 FROM Admin a
                        WHERE a.user.userId = :userId AND a.board = b AND a.isActive = true
                    )
                  )
              AND (
                    b.isPublic = true
                    OR (LOWER(b.boardUrl) = :inquiryBoardUrl AND p.userId = :userId)
                    OR :viewerIsSuperAdmin = true
                    OR EXISTS (
                        SELECT 1 FROM Admin a
                        WHERE a.user.userId = :userId AND a.board = b AND a.isActive = true
                    )
                  )
              AND (
                    p.isSecret = false
                    OR p.userId = :userId
                    OR :viewerIsSuperAdmin = true
                    OR EXISTS (
                        SELECT 1 FROM Admin a
                        WHERE a.user.userId = :userId AND a.board = b AND a.isActive = true
                    )
                  )
            """;
}
