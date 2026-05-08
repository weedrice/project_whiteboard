package com.weedrice.whiteboard.domain.feed.repository;

import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import com.weedrice.whiteboard.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserFeedRepositoryCustomImpl implements UserFeedRepositoryCustom {

    private static final String CONTENT_TYPE_POST = "POST";
    private static final String INQUIRY_BOARD_URL = "inquiry";

    private final EntityManager entityManager;

    @Override
    public Page<UserFeed> findVisibleByTargetUserOrderByCreatedAtDesc(
            User targetUser,
            Collection<Long> blockedUserIds,
            Pageable pageable) {
        String fromClause = buildVisibleFeedFromClause(blockedUserIds);
        TypedQuery<UserFeed> contentQuery = entityManager.createQuery(
                "SELECT uf " + fromClause + " ORDER BY uf.createdAt DESC, uf.feedId DESC",
                UserFeed.class);
        bindParameters(contentQuery, targetUser, blockedUserIds);
        if (pageable.isPaged()) {
            contentQuery.setFirstResult((int) pageable.getOffset());
            contentQuery.setMaxResults(pageable.getPageSize());
        }

        TypedQuery<Long> countQuery = entityManager.createQuery("SELECT COUNT(uf) " + fromClause, Long.class);
        bindParameters(countQuery, targetUser, blockedUserIds);

        List<UserFeed> content = contentQuery.getResultList();
        Long total = countQuery.getSingleResult();
        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    private String buildVisibleFeedFromClause(Collection<Long> blockedUserIds) {
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
        if (blockedUserIds != null && !blockedUserIds.isEmpty()) {
            query.append("\n              AND p.user.userId NOT IN :blockedUserIds");
        }
        query.append("""

                              AND (
                                    b.isActive = true
                                    OR p.user = :targetUser
                                    OR :isSuperAdmin = true
                                    OR EXISTS (
                                        SELECT 1
                                        FROM Admin admin
                                        WHERE admin.board = b
                                          AND admin.user = :targetUser
                                          AND admin.isActive = true
                                    )
                              )
                              AND (
                                    b.isPublic = true
                                    OR (
                                        LOWER(b.boardUrl) = :inquiryBoardUrl
                                        AND p.user = :targetUser
                                    )
                                    OR :isSuperAdmin = true
                                    OR EXISTS (
                                        SELECT 1
                                        FROM Admin admin
                                        WHERE admin.board = b
                                          AND admin.user = :targetUser
                                          AND admin.isActive = true
                                    )
                              )
                              AND (
                                    p.isSecret = false
                                    OR p.user = :targetUser
                                    OR :isSuperAdmin = true
                                    OR EXISTS (
                                        SELECT 1
                                        FROM Admin admin
                                        WHERE admin.board = b
                                          AND admin.user = :targetUser
                                          AND admin.isActive = true
                                    )
                              )
                        )
                  )
                """);
        return query.toString();
    }

    private void bindParameters(TypedQuery<?> query, User targetUser, Collection<Long> blockedUserIds) {
        query.setParameter("targetUser", targetUser);
        query.setParameter("postContentType", CONTENT_TYPE_POST);
        query.setParameter("inquiryBoardUrl", INQUIRY_BOARD_URL);
        query.setParameter("isSuperAdmin", targetUser.isUsableSuperAdmin());
        if (blockedUserIds != null && !blockedUserIds.isEmpty()) {
            query.setParameter("blockedUserIds", blockedUserIds);
        }
    }
}
