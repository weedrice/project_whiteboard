package com.weedrice.whiteboard.domain.post.repository;

public final class PostVisibilityJpql {

    private PostVisibilityJpql() {
    }

    public static final String VIEWER_READABLE_POST = """
              AND (
                    b.isActive = true
                    OR p.user = :user
                    OR :viewerIsSuperAdmin = true
                    OR EXISTS (
                        SELECT 1
                        FROM Admin a
                        WHERE a.user = :user
                          AND a.board = b
                          AND a.isActive = true
                    )
                  )
              AND (
                    b.isPublic = true
                    OR (LOWER(b.boardUrl) = :inquiryBoardUrl AND p.user = :user)
                    OR :viewerIsSuperAdmin = true
                    OR EXISTS (
                        SELECT 1
                        FROM Admin a
                        WHERE a.user = :user
                          AND a.board = b
                          AND a.isActive = true
                    )
                  )
              AND (
                    p.isSecret = false
                    OR p.user = :user
                    OR :viewerIsSuperAdmin = true
                    OR EXISTS (
                        SELECT 1
                        FROM Admin a
                        WHERE a.user = :user
                          AND a.board = b
                          AND a.isActive = true
                    )
                  )
            """;
}
