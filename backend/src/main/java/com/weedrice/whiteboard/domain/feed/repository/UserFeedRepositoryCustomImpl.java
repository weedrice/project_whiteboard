package com.weedrice.whiteboard.domain.feed.repository;

import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserFeedRepositoryCustomImpl implements UserFeedRepositoryCustom {

    private static final String CONTENT_TYPE_POST = "POST";

    private final EntityManager entityManager;

    @Override
    public Page<UserFeed> findVisibleByTargetUserOrderByCreatedAtDesc(
            UserFeedVisibilityCondition visibilityCondition,
            Pageable pageable) {
        String fromClause = buildVisibleFeedFromClause(visibilityCondition);
        TypedQuery<UserFeed> contentQuery = entityManager.createQuery(
                "SELECT uf " + fromClause + " ORDER BY uf.createdAt DESC, uf.feedId DESC",
                UserFeed.class);
        bindParameters(contentQuery, visibilityCondition);
        if (pageable.isPaged()) {
            contentQuery.setFirstResult((int) pageable.getOffset());
            contentQuery.setMaxResults(pageable.getPageSize());
        }

        TypedQuery<Long> countQuery = entityManager.createQuery("SELECT COUNT(uf) " + fromClause, Long.class);
        bindParameters(countQuery, visibilityCondition);

        List<UserFeed> content = contentQuery.getResultList();
        Long total = countQuery.getSingleResult();
        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    private String buildVisibleFeedFromClause(UserFeedVisibilityCondition visibilityCondition) {
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
                """);
        if (visibilityCondition.hasBlockedUserIds()) {
            query.append("\n              AND p.user.userId NOT IN :blockedUserIds");
        }
        query.append("""

                              AND (
                                    b.isActive = true
                                    OR p.user = :targetUser
                                    OR :isSuperAdmin = true
                """);
        appendAdminBoardAccess(query, visibilityCondition);
        query.append("""
                              )
                              AND (
                                    b.isPublic = true
                                    OR (
                                        LOWER(b.boardUrl) = :inquiryBoardUrl
                                        AND p.user = :targetUser
                                    )
                                    OR :isSuperAdmin = true
                """);
        appendAdminBoardAccess(query, visibilityCondition);
        query.append("""
                              )
                              AND (
                                    p.isSecret = false
                                    OR p.user = :targetUser
                                    OR :isSuperAdmin = true
                """);
        appendAdminBoardAccess(query, visibilityCondition);
        query.append("""
                              )
                        )
                  )
                """);
        return query.toString();
    }

    private void appendAdminBoardAccess(StringBuilder query, UserFeedVisibilityCondition visibilityCondition) {
        if (visibilityCondition.hasActiveAdminBoardIds()) {
            query.append("\n                                    OR b.boardId IN :activeAdminBoardIds");
        }
    }

    private void bindParameters(TypedQuery<?> query, UserFeedVisibilityCondition visibilityCondition) {
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
}
