package com.weedrice.whiteboard.domain.comment.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static com.weedrice.whiteboard.domain.comment.entity.QComment.comment;

@Repository
@RequiredArgsConstructor
public class CommentRepositoryCustomImpl implements CommentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Comment> searchCommentsByKeyword(String keyword, List<Long> blockedUserIds, Long viewerUserId,
            Pageable pageable) {
        return searchCommentsByKeyword(keyword, null, blockedUserIds, false, viewerUserId, pageable);
    }

    @Override
    public Page<Comment> searchCommentsByKeyword(String keyword, String boardUrl, List<Long> blockedUserIds,
            boolean includeSecret, Long viewerUserId, Pageable pageable) {
        SearchQueryParts parts = buildSearchQueryParts(
                keyword, boardUrl, blockedUserIds, includeSecret, viewerUserId);
        TypedQuery<Comment> contentQuery = entityManager.createQuery(
                "SELECT DISTINCT c "
                        + parts.fromAndWhere().replaceFirst("\\bWHERE\\b", "LEFT JOIN FETCH c.parent WHERE")
                        + " ORDER BY c.createdAt DESC, c.commentId DESC",
                Comment.class);
        applyParameters(contentQuery, parts.parameters());
        contentQuery.setFirstResult((int) pageable.getOffset());
        contentQuery.setMaxResults(pageable.getPageSize());

        TypedQuery<Long> countQuery = entityManager.createQuery(
                "SELECT COUNT(DISTINCT c) " + parts.fromAndWhere(), Long.class);
        applyParameters(countQuery, parts.parameters());
        return new PageImpl<>(contentQuery.getResultList(), pageable, countQuery.getSingleResult());
    }

    @Override
    public java.util.Optional<Comment> findByIdWithRelations(@org.jspecify.annotations.NonNull Long commentId) {
        Comment result = queryFactory
                .selectFrom(comment)
                .leftJoin(comment.parent).fetchJoin()
                .where(comment.commentId.eq(commentId))
                .fetchOne();
        return java.util.Optional.ofNullable(result);
    }

    @Override
    public java.util.Optional<Comment> findNonDeletedByIdWithRelations(@org.jspecify.annotations.NonNull Long commentId) {
        Comment result = queryFactory
                .selectFrom(comment)
                .leftJoin(comment.parent).fetchJoin()
                .where(
                        comment.commentId.eq(commentId),
                        comment.isDeleted.eq(false))
                .fetchOne();
        return java.util.Optional.ofNullable(result);
    }

    private SearchQueryParts buildSearchQueryParts(String keyword, String boardUrl, List<Long> blockedUserIds,
            boolean includeSecret, Long viewerUserId) {
        StringBuilder query = new StringBuilder("""
                FROM Comment c
                JOIN Post p ON p.postId = c.postId
                JOIN p.board b
                WHERE c.isDeleted = false
                  AND c.isBlinded = false
                  AND p.isDeleted = false
                  AND p.isBlinded = false
                """);
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (StringUtils.hasText(keyword)) {
            query.append(" AND LOWER(c.content) LIKE :keyword ESCAPE '!' ");
            parameters.put("keyword", containsPattern(keyword));
        }
        if (StringUtils.hasText(boardUrl)) {
            query.append(" AND b.boardUrl = :boardUrl ");
            parameters.put("boardUrl", boardUrl);
        } else {
            query.append(" AND b.isActive = true AND b.isPublic = true ")
                    .append(" AND (b.isListed = true OR b.isListed IS NULL) ");
        }
        if (!includeSecret) {
            if (viewerUserId == null) {
                query.append(" AND p.isSecret = false ");
            } else {
                query.append(" AND (p.isSecret = false OR p.userId = :viewerUserId) ");
                parameters.put("viewerUserId", viewerUserId);
            }
        }
        if (blockedUserIds != null && !blockedUserIds.isEmpty()) {
            query.append(" AND c.userId NOT IN :blockedUserIds AND p.userId NOT IN :blockedUserIds ");
            parameters.put("blockedUserIds", blockedUserIds);
        }
        return new SearchQueryParts(query.toString(), parameters);
    }

    private String containsPattern(String keyword) {
        StringBuilder escaped = new StringBuilder();
        for (char ch : keyword.toLowerCase(Locale.ROOT).toCharArray()) {
            if (ch == '!' || ch == '%' || ch == '_') {
                escaped.append('!');
            }
            escaped.append(ch);
        }
        return "%" + escaped + "%";
    }

    private void applyParameters(jakarta.persistence.Query query, Map<String, Object> parameters) {
        parameters.forEach(query::setParameter);
    }

    private record SearchQueryParts(String fromAndWhere, Map<String, Object> parameters) {
    }
}
