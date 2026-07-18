package com.weedrice.whiteboard.domain.feed.repository;

import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import jakarta.persistence.TypedQuery;

final class UserFeedVisibilityQuerySpec {

    private static final String CONTENT_TYPE_POST = "POST";

    private UserFeedVisibilityQuerySpec() {
    }

    static String buildFromClause(UserFeedVisibilityCondition visibilityCondition) {
        StringBuilder query = new StringBuilder("""
                FROM UserFeed uf
                LEFT JOIN Post p ON uf.contentType = :postContentType AND p.postId = uf.contentId
                LEFT JOIN p.board b
                WHERE uf.targetUser = :targetUser
                  AND (
                        uf.contentType <> :postContentType
                        OR (
                              p.postId IS NOT NULL
                              AND p.isDeleted = false
                              AND p.isBlinded = false
                """);
        appendBlockedAuthorCondition(query, visibilityCondition);
        appendReadablePostCondition(query, visibilityCondition);
        query.append("""
                        )
                  )
                """);
        return query.toString();
    }

    static void bindParameters(TypedQuery<?> query, UserFeedVisibilityCondition visibilityCondition) {
        query.setParameter("targetUser", visibilityCondition.targetUser());
        query.setParameter("postContentType", CONTENT_TYPE_POST);
        query.setParameter("inquiryBoardUrl", BoardPolicyConstants.INQUIRY_BOARD_URL);
        query.setParameter("isSuperAdmin", visibilityCondition.superAdmin());
        if (visibilityCondition.hasBlockedUserIds()) {
            query.setParameter("blockedUserIds", visibilityCondition.blockedUserIds());
        }
        if (visibilityCondition.hasActiveAdminBoardIds()) {
            query.setParameter("activeAdminBoardIds", visibilityCondition.activeAdminBoardIds());
        }
    }

    private static void appendBlockedAuthorCondition(StringBuilder query,
                                                     UserFeedVisibilityCondition visibilityCondition) {
        if (visibilityCondition.hasBlockedUserIds()) {
            query.append("\n              AND p.user.userId NOT IN :blockedUserIds");
        }
    }

    private static void appendReadablePostCondition(StringBuilder query,
                                                    UserFeedVisibilityCondition visibilityCondition) {
        appendBoardActiveCondition(query, visibilityCondition);
        appendBoardPublicCondition(query, visibilityCondition);
        appendSecretPostCondition(query, visibilityCondition);
    }

    private static void appendBoardActiveCondition(StringBuilder query,
                                                   UserFeedVisibilityCondition visibilityCondition) {
        query.append("""

                              AND (
                                    b.isActive = true
                                    OR p.user = :targetUser
                                    OR :isSuperAdmin = true
                """);
        appendAdminBoardAccess(query, visibilityCondition);
        query.append("""
                              )
                """);
    }

    private static void appendBoardPublicCondition(StringBuilder query,
                                                   UserFeedVisibilityCondition visibilityCondition) {
        query.append("""
                              AND (
                                    b.isPublic = true
                                    OR (
                                        LOWER(TRIM(b.boardUrl)) = :inquiryBoardUrl
                                        AND p.user = :targetUser
                                    )
                                    OR :isSuperAdmin = true
                """);
        appendAdminBoardAccess(query, visibilityCondition);
        query.append("""
                              )
                """);
    }

    private static void appendSecretPostCondition(StringBuilder query,
                                                  UserFeedVisibilityCondition visibilityCondition) {
        query.append("""
                              AND (
                                    p.isSecret = false
                                    OR p.user = :targetUser
                                    OR :isSuperAdmin = true
                """);
        appendAdminBoardAccess(query, visibilityCondition);
        query.append("""
                              )
                """);
    }

    private static void appendAdminBoardAccess(StringBuilder query, UserFeedVisibilityCondition visibilityCondition) {
        if (visibilityCondition.hasActiveAdminBoardIds()) {
            query.append("\n                                    OR b.boardId IN :activeAdminBoardIds");
        }
    }
}
