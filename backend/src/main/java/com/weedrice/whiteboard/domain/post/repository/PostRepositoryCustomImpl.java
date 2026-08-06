package com.weedrice.whiteboard.domain.post.repository;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weedrice.whiteboard.domain.post.entity.Post;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static com.weedrice.whiteboard.domain.file.entity.QFile.file;
import static com.weedrice.whiteboard.domain.post.entity.QPost.post;
import static com.weedrice.whiteboard.domain.tag.entity.QPostTag.postTag;

@Repository
@RequiredArgsConstructor
public class PostRepositoryCustomImpl implements PostRepositoryCustom {

    static final int POST_LIST_CONTENT_PREVIEW_LENGTH = 4096;

    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;

    @Override
    public Page<Post> findByBoardIdAndCategoryId(Long boardId, Long categoryId, String keyword, Integer minLikes,
            List<Long> blockedUserIds, Boolean includeSecret, Long viewerUserId, @NonNull Pageable pageable) {
        List<Post> content = queryFactory
                .selectFrom(post)
                .join(post.user).fetchJoin()
                .leftJoin(post.agent).fetchJoin()
                .join(post.board).fetchJoin()
                .leftJoin(post.category).fetchJoin()
                .where(
                        post.board.boardId.eq(boardId),
                        categoryIdEq(categoryId),
                        keywordContains(keyword),
                        minLikesGoe(minLikes),
                        post.isDeleted.eq(false),
                        post.isBlinded.eq(false),
                        secretCondition(includeSecret, viewerUserId),
                        notBlockedCondition(blockedUserIds))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(getOrderSpecifiers(pageable))
                .fetch();

        Long total = queryFactory
                .select(post.count())
                .from(post)
                .where(
                        post.board.boardId.eq(boardId),
                        categoryIdEq(categoryId),
                        keywordContains(keyword),
                        minLikesGoe(minLikes),
                        post.isDeleted.eq(false),
                        post.isBlinded.eq(false),
                        secretCondition(includeSecret, viewerUserId),
                        notBlockedCondition(blockedUserIds))
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public Page<PostListSummaryProjection> findPostListSummariesByBoardIdAndCategoryId(
            Long boardId,
            Long categoryId,
            String keyword,
            Integer minLikes,
            List<Long> blockedUserIds,
            Boolean includeSecret,
            Long viewerUserId,
            @NonNull Pageable pageable) {
        List<PostListSummaryProjection> content = queryFactory
                .select(Projections.constructor(
                        PostListSummaryProjection.class,
                        post.postId,
                        post.board.boardId,
                        post.category.categoryId,
                        post.title,
                        post.user.userId,
                        post.agent.agentId,
                        post.user.displayName,
                        post.user.profileImageUrl,
                        post.agent.name,
                        post.category.name,
                        post.viewCount,
                        post.likeCount,
                        post.commentCount,
                        post.isNotice,
                        post.isNsfw,
                        post.isSpoiler,
                        post.isSecret,
                        post.pinnedAt,
                        post.createdAt,
                        post.board.boardUrl,
                        post.board.boardName,
                        post.board.iconUrl,
                        Expressions.stringTemplate(
                                "substring({0}, 1, {1})",
                                post.contents,
                                POST_LIST_CONTENT_PREVIEW_LENGTH)))
                .from(post)
                .join(post.user)
                .leftJoin(post.agent)
                .join(post.board)
                .leftJoin(post.category)
                .where(
                        post.board.boardId.eq(boardId),
                        categoryIdEq(categoryId),
                        keywordContains(keyword),
                        minLikesGoe(minLikes),
                        post.isDeleted.eq(false),
                        post.isBlinded.eq(false),
                        secretCondition(includeSecret, viewerUserId),
                        notBlockedCondition(blockedUserIds))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(getOrderSpecifiers(pageable))
                .fetch();

        Long total = queryFactory
                .select(post.count())
                .from(post)
                .where(
                        post.board.boardId.eq(boardId),
                        categoryIdEq(categoryId),
                        keywordContains(keyword),
                        minLikesGoe(minLikes),
                        post.isDeleted.eq(false),
                        post.isBlinded.eq(false),
                        secretCondition(includeSecret, viewerUserId),
                        notBlockedCondition(blockedUserIds))
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public long countPostsBeforeInBoardDefaultOrder(Long boardId, LocalDateTime createdAt, Long postId,
            List<Long> blockedUserIds, Boolean includeSecret, Long viewerUserId) {
        if (boardId == null || createdAt == null || postId == null) {
            return 0L;
        }

        Long total = queryFactory
                .select(post.count())
                .from(post)
                .where(
                        post.board.boardId.eq(boardId),
                        post.isDeleted.eq(false),
                        post.isBlinded.eq(false),
                        secretCondition(includeSecret, viewerUserId),
                        notBlockedCondition(blockedUserIds),
                        post.createdAt.gt(createdAt)
                                .or(post.createdAt.eq(createdAt).and(post.postId.gt(postId))))
                .fetchOne();

        return total != null ? total : 0L;
    }

    @Override
    public Page<Post> searchPostsByKeyword(String keyword, List<Long> blockedUserIds, Long viewerUserId,
            @NonNull Pageable pageable) {
        BooleanExpression keywordExpression = StringUtils.hasText(keyword)
                ? post.title.containsIgnoreCase(keyword).or(post.contents.containsIgnoreCase(keyword))
                : null;

        List<Post> content = queryFactory
                .selectFrom(post)
                .join(post.user).fetchJoin()
                .leftJoin(post.agent).fetchJoin()
                .join(post.board).fetchJoin()
                .leftJoin(post.category).fetchJoin()
                .where(
                        keywordExpression,
                        post.isDeleted.eq(false),
                        post.isBlinded.eq(false),
                        post.board.isActive.eq(true),
                        post.board.isPublic.eq(true),
                        listedBoard(),
                        secretCondition(false, viewerUserId),
                        notBlockedCondition(blockedUserIds))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(getOrderSpecifiers(pageable))
                .fetch();

        Long total = queryFactory
                .select(post.count())
                .from(post)
                .where(
                        keywordExpression,
                        post.isDeleted.eq(false),
                        post.isBlinded.eq(false),
                        post.board.isActive.eq(true),
                        post.board.isPublic.eq(true),
                        listedBoard(),
                        secretCondition(false, viewerUserId),
                        notBlockedCondition(blockedUserIds))
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public Page<Post> searchPosts(String keyword, String searchType, String boardUrl, String author,
            LocalDateTime createdFrom, LocalDateTime createdTo, List<Long> blockedUserIds,
            Boolean includeSecret, Long viewerUserId, @NonNull Pageable pageable) {
        BooleanExpression searchCondition = null;
        if (StringUtils.hasText(keyword)) {
            if ("TITLE".equalsIgnoreCase(searchType)) {
                searchCondition = post.title.containsIgnoreCase(keyword);
            } else if ("CONTENT".equalsIgnoreCase(searchType)) {
                searchCondition = post.contents.containsIgnoreCase(keyword);
            } else if ("AUTHOR".equalsIgnoreCase(searchType)) {
                searchCondition = displayAuthorContains(keyword);
            } else {
                searchCondition = post.title.containsIgnoreCase(keyword)
                        .or(post.contents.containsIgnoreCase(keyword));
            }
        }

        BooleanExpression boardCondition = null;
        if (StringUtils.hasText(boardUrl)) {
            boardCondition = post.board.boardUrl.eq(boardUrl);
        }
        BooleanExpression authorCondition = StringUtils.hasText(author) ? displayAuthorContains(author) : null;
        BooleanExpression createdFromCondition = createdFrom != null ? post.createdAt.goe(createdFrom) : null;
        BooleanExpression createdToCondition = createdTo != null ? post.createdAt.lt(createdTo) : null;

        List<Post> content = queryFactory
                .selectFrom(post)
                .join(post.user).fetchJoin()
                .leftJoin(post.agent).fetchJoin()
                .join(post.board).fetchJoin()
                .leftJoin(post.category).fetchJoin()
                .where(
                        searchCondition,
                        boardCondition,
                        authorCondition,
                        createdFromCondition,
                        createdToCondition,
                        post.isDeleted.eq(false),
                        post.isBlinded.eq(false),
                        activeBoardOnlyForGlobalSearch(boardUrl),
                        publicBoardOnlyForGlobalSearch(boardUrl),
                        listedBoardOnlyForGlobalSearch(boardUrl),
                        searchSecretCondition(boardUrl, includeSecret, viewerUserId),
                        notBlockedCondition(blockedUserIds))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(getOrderSpecifiers(pageable))
                .fetch();

        Long total = queryFactory
                .select(post.count())
                .from(post)
                .leftJoin(post.agent)
                .where(
                        searchCondition,
                        boardCondition,
                        authorCondition,
                        createdFromCondition,
                        createdToCondition,
                        post.isDeleted.eq(false),
                        post.isBlinded.eq(false),
                        activeBoardOnlyForGlobalSearch(boardUrl),
                        publicBoardOnlyForGlobalSearch(boardUrl),
                        listedBoardOnlyForGlobalSearch(boardUrl),
                        searchSecretCondition(boardUrl, includeSecret, viewerUserId),
                        notBlockedCondition(blockedUserIds))
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public Page<Post> findByTagId(Long tagId, List<Long> blockedUserIds, @NonNull Pageable pageable) {
        List<Post> content = queryFactory
                .select(post)
                .from(postTag)
                .join(postTag.post, post)
                .join(post.user).fetchJoin()
                .leftJoin(post.agent).fetchJoin()
                .join(post.board).fetchJoin()
                .leftJoin(post.category).fetchJoin()
                .where(
                        postTag.tag.tagId.eq(tagId),
                        post.isDeleted.eq(false),
                        post.isBlinded.eq(false),
                        post.board.isActive.eq(true),
                        post.board.isPublic.eq(true),
                        listedBoard(),
                        post.isSecret.eq(false),
                        notBlockedCondition(blockedUserIds))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(getOrderSpecifiers(pageable))
                .fetch();

        Long total = queryFactory
                .select(postTag.count())
                .from(postTag)
                .where(
                        postTag.tag.tagId.eq(tagId),
                        postTag.post.isDeleted.eq(false),
                        postTag.post.isBlinded.eq(false),
                        postTag.post.board.isActive.eq(true),
                        postTag.post.board.isPublic.eq(true),
                        postTag.post.board.isListed.eq(true).or(postTag.post.board.isListed.isNull()),
                        postTag.post.isSecret.eq(false),
                        notBlockedPostTagCondition(blockedUserIds))
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public List<Post> findNoticesByBoardId(Long boardId, Boolean isNotice, Boolean isDeleted,
            List<Long> blockedUserIds, Boolean includeSecret, Long viewerUserId) {
        return queryFactory
                .selectFrom(post)
                .join(post.user).fetchJoin()
                .leftJoin(post.agent).fetchJoin()
                .join(post.board).fetchJoin()
                .leftJoin(post.category).fetchJoin()
                .where(
                        post.board.boardId.eq(boardId),
                        post.isNotice.eq(isNotice),
                        post.isDeleted.eq(isDeleted),
                        post.isBlinded.eq(false),
                        secretCondition(includeSecret, viewerUserId),
                        notBlockedCondition(blockedUserIds))
                .orderBy(post.createdAt.desc())
                .fetch();
    }

    @Override
    public List<Post> findLatestPostsByBoardId(Long boardId, Boolean isDeleted, List<Long> blockedUserIds,
            Boolean includeSecret, Long viewerUserId, Pageable pageable) {
        return queryFactory
                .selectFrom(post)
                .join(post.user).fetchJoin()
                .leftJoin(post.agent).fetchJoin()
                .join(post.board).fetchJoin()
                .leftJoin(post.category).fetchJoin()
                .where(
                        post.board.boardId.eq(boardId),
                        post.isDeleted.eq(isDeleted),
                        post.isBlinded.eq(false),
                        secretCondition(includeSecret, viewerUserId),
                        notBlockedCondition(blockedUserIds))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(post.createdAt.desc())
                .fetch();
    }

    @Override
    public List<Post> findTrendingPosts(LocalDateTime since, List<Long> blockedUserIds, Pageable pageable) {
        return findTrendingPosts(since, blockedUserIds, pageable.getOffset(), pageable.getPageSize());
    }

    @Override
    public List<Post> findTrendingPosts(LocalDateTime since, List<Long> blockedUserIds, long offset, int limit) {
        return queryFactory
                .selectFrom(post)
                .join(post.user).fetchJoin()
                .leftJoin(post.agent).fetchJoin()
                .join(post.board).fetchJoin()
                .leftJoin(post.category).fetchJoin()
                .where(trendingPostConditions(since, blockedUserIds))
                .orderBy(
                        TrendingPostRankingPolicy.score(post).desc(),
                        post.createdAt.desc(),
                        post.postId.desc())
                .offset(offset)
                .limit(limit)
                .fetch();
    }

    @Override
    public long countTrendingPosts(LocalDateTime since, List<Long> blockedUserIds) {
        Long total = queryFactory
                .select(post.count())
                .from(post)
                .where(trendingPostConditions(since, blockedUserIds))
                .fetchOne();
        return total != null ? total : 0L;
    }

    @Override
    public List<Post> findPublicLandingLatestPosts(String inquiryBoardUrl, List<Long> blockedUserIds,
            Pageable pageable) {
        return queryFactory
                .selectFrom(post)
                .join(post.user).fetchJoin()
                .leftJoin(post.agent).fetchJoin()
                .join(post.board).fetchJoin()
                .leftJoin(post.category).fetchJoin()
                .where(publicLandingLatestPostConditions(inquiryBoardUrl, blockedUserIds))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(post.createdAt.desc(), post.postId.desc())
                .fetch();
    }

    @Override
    public Page<Post> findAgentFeedByBoardIds(Collection<Long> boardIds, List<Long> blockedUserIds,
            Collection<Long> secretVisibleBoardIds, Long viewerUserId, @NonNull Pageable pageable) {
        if (boardIds == null || boardIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Post> content = queryFactory
                .selectFrom(post)
                .join(post.user).fetchJoin()
                .leftJoin(post.agent).fetchJoin()
                .join(post.board).fetchJoin()
                .leftJoin(post.category).fetchJoin()
                .where(
                        post.board.boardId.in(boardIds),
                        post.isDeleted.eq(false),
                        post.isBlinded.eq(false),
                        post.board.isActive.eq(true),
                        post.board.isPublic.eq(true),
                        agentFeedSecretCondition(secretVisibleBoardIds, viewerUserId),
                        notBlockedCondition(blockedUserIds))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(post.createdAt.desc(), post.postId.desc())
                .fetch();

        Long total = queryFactory
                .select(post.count())
                .from(post)
                .where(
                        post.board.boardId.in(boardIds),
                        post.isDeleted.eq(false),
                        post.isBlinded.eq(false),
                        post.board.isActive.eq(true),
                        post.board.isPublic.eq(true),
                        agentFeedSecretCondition(secretVisibleBoardIds, viewerUserId),
                        notBlockedCondition(blockedUserIds))
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public List<Long> findLatestPostIdsByBoardIds(Collection<Long> boardIds, int limitPerBoard, List<Long> blockedUserIds,
            Collection<Long> secretVisibleBoardIds, Long viewerUserId) {
        if (boardIds == null || boardIds.isEmpty() || limitPerBoard <= 0) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder("""
                SELECT ranked.post_id
                FROM (
                    SELECT p.post_id, p.board_id, p.created_at,
                           ROW_NUMBER() OVER (PARTITION BY p.board_id ORDER BY p.created_at DESC, p.post_id DESC) AS rn
                    FROM posts p
                    WHERE p.board_id IN (:boardIds)
                      AND p.is_deleted = 'N'
                      AND p.is_blinded = 'N'
                """);

        appendSecretVisibilityCondition(sql, viewerUserId, secretVisibleBoardIds);
        if (blockedUserIds != null && !blockedUserIds.isEmpty()) {
            sql.append("\n  AND p.user_id NOT IN (:blockedUserIds)");
        }

        sql.append("""

                ) ranked
                WHERE ranked.rn <= :limitPerBoard
                ORDER BY ranked.board_id ASC, ranked.created_at DESC, ranked.post_id DESC
                """);

        var query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("boardIds", boardIds);
        query.setParameter("limitPerBoard", limitPerBoard);
        if (viewerUserId != null) {
            query.setParameter("viewerUserId", viewerUserId);
        }
        if (secretVisibleBoardIds != null && !secretVisibleBoardIds.isEmpty()) {
            query.setParameter("secretVisibleBoardIds", secretVisibleBoardIds);
        }
        if (blockedUserIds != null && !blockedUserIds.isEmpty()) {
            query.setParameter("blockedUserIds", blockedUserIds);
        }

        @SuppressWarnings("unchecked")
        List<Number> results = query.getResultList();
        return results.stream()
                .map(Number::longValue)
                .toList();
    }

    private BooleanExpression notBlockedCondition(List<Long> blockedUserIds) {
        return (blockedUserIds != null && !blockedUserIds.isEmpty()) ? post.user.userId.notIn(blockedUserIds) : null;
    }

    private BooleanExpression agentFeedSecretCondition(Collection<Long> secretVisibleBoardIds, Long viewerUserId) {
        BooleanExpression expression = post.isSecret.eq(false);

        if (viewerUserId != null) {
            expression = expression.or(post.user.userId.eq(viewerUserId));
        }
        if (secretVisibleBoardIds != null && !secretVisibleBoardIds.isEmpty()) {
            expression = expression.or(post.board.boardId.in(secretVisibleBoardIds));
        }
        return expression;
    }

    private void appendSecretVisibilityCondition(StringBuilder sql, Long viewerUserId,
            Collection<Long> secretVisibleBoardIds) {
        boolean hasViewer = viewerUserId != null;
        boolean hasSecretVisibleBoards = secretVisibleBoardIds != null && !secretVisibleBoardIds.isEmpty();

        if (!hasViewer && !hasSecretVisibleBoards) {
            sql.append("\n  AND p.is_secret = 'N'");
            return;
        }

        sql.append("\n  AND (p.is_secret = 'N'");
        if (hasViewer) {
            sql.append(" OR p.user_id = :viewerUserId");
        }
        if (hasSecretVisibleBoards) {
            sql.append(" OR p.board_id IN (:secretVisibleBoardIds)");
        }
        sql.append(")");
    }

    private BooleanExpression notBlockedPostTagCondition(List<Long> blockedUserIds) {
        return (blockedUserIds != null && !blockedUserIds.isEmpty())
                ? postTag.post.user.userId.notIn(blockedUserIds)
                : null;
    }

    private BooleanExpression categoryIdEq(Long categoryId) {
        return categoryId != null ? post.category.categoryId.eq(categoryId) : null;
    }

    private BooleanExpression keywordContains(String keyword) {
        return StringUtils.hasText(keyword)
                ? post.title.containsIgnoreCase(keyword).or(post.contents.containsIgnoreCase(keyword))
                : null;
    }

    private BooleanExpression displayAuthorContains(String keyword) {
        return post.user.displayName.containsIgnoreCase(keyword)
                .or(post.agent.name.containsIgnoreCase(keyword));
    }

    private BooleanExpression minLikesGoe(Integer minLikes) {
        return minLikes != null ? post.likeCount.goe(minLikes) : null;
    }

    private BooleanExpression[] trendingPostConditions(LocalDateTime since, List<Long> blockedUserIds) {
        return new BooleanExpression[] {
                post.createdAt.goe(since),
                post.isDeleted.eq(false),
                post.isBlinded.eq(false),
                post.isNotice.eq(false),
                post.isSecret.eq(false),
                post.board.isActive.eq(true),
                post.board.isPublic.eq(true),
                listedBoard(),
                notBlockedCondition(blockedUserIds),
                TrendingPostRankingPolicy.mediaCondition(post, file)
        };
    }

    private BooleanExpression[] publicLandingLatestPostConditions(String inquiryBoardUrl, List<Long> blockedUserIds) {
        return new BooleanExpression[] {
                post.isDeleted.eq(false),
                post.isBlinded.eq(false),
                post.isNotice.eq(false),
                post.isSecret.eq(false),
                post.board.isActive.eq(true),
                post.board.isPublic.eq(true),
                listedBoard(),
                post.board.boardUrl.lower().ne(inquiryBoardUrl),
                notBlockedCondition(blockedUserIds)
        };
    }

    private BooleanExpression secretCondition(Boolean includeSecret, Long viewerUserId) {
        if (Boolean.TRUE.equals(includeSecret)) {
            return null;
        }
        if (viewerUserId != null) {
            return post.isSecret.eq(false).or(post.user.userId.eq(viewerUserId));
        }
        return post.isSecret.eq(false);
    }

    private BooleanExpression activeBoardOnlyForGlobalSearch(String boardUrl) {
        return StringUtils.hasText(boardUrl) ? null : post.board.isActive.eq(true);
    }

    private BooleanExpression publicBoardOnlyForGlobalSearch(String boardUrl) {
        return StringUtils.hasText(boardUrl) ? null : post.board.isPublic.eq(true);
    }

    private BooleanExpression listedBoardOnlyForGlobalSearch(String boardUrl) {
        return StringUtils.hasText(boardUrl) ? null : listedBoard();
    }

    private BooleanExpression listedBoard() {
        return post.board.isListed.eq(true).or(post.board.isListed.isNull());
    }

    private BooleanExpression searchSecretCondition(String boardUrl, Boolean includeSecret, Long viewerUserId) {
        if (StringUtils.hasText(boardUrl) && Boolean.TRUE.equals(includeSecret)) {
            return null;
        }
        return secretCondition(includeSecret, viewerUserId);
    }

    @Override
    public java.util.Optional<Post> findByIdWithRelations(@NonNull Long postId) {
        Post result = queryFactory
                .selectFrom(post)
                .join(post.user).fetchJoin()
                .leftJoin(post.agent).fetchJoin()
                .join(post.board).fetchJoin()
                .leftJoin(post.category).fetchJoin()
                .where(post.postId.eq(postId))
                .fetchOne();
        return java.util.Optional.ofNullable(result);
    }

    private OrderSpecifier<?>[] getOrderSpecifiers(Pageable pageable) {
        if (!pageable.getSort().isEmpty()) {
            boolean allAllowed = pageable.getSort().stream()
                    .allMatch(order -> switch (order.getProperty()) {
                        case "viewCount", "likeCount", "pinnedAt", "createdAt", "postId" -> true;
                        default -> false;
                    });
            if (!allAllowed) {
                return defaultOrderSpecifiers();
            }
            return pageable.getSort().stream().map(order -> {
                Order direction = order.getDirection().isAscending() ? Order.ASC : Order.DESC;
                switch (order.getProperty()) {
                    case "viewCount":
                        return new OrderSpecifier<>(direction, post.viewCount);
                    case "likeCount":
                        return new OrderSpecifier<>(direction, post.likeCount);
                    case "pinnedAt":
                        return new OrderSpecifier<>(direction, post.pinnedAt).nullsLast();
                    case "createdAt":
                        return new OrderSpecifier<>(direction, post.createdAt);
                    case "postId":
                        return new OrderSpecifier<>(direction, post.postId);
                    default:
                        throw new IllegalStateException("Unsupported post sort property: " + order.getProperty());
                }
            }).toArray(OrderSpecifier[]::new);
        }
        return defaultOrderSpecifiers();
    }

    private OrderSpecifier<?>[] defaultOrderSpecifiers() {
        return new OrderSpecifier[] {
                new OrderSpecifier<>(Order.DESC, post.pinnedAt).nullsLast(),
                new OrderSpecifier<>(Order.DESC, post.createdAt),
                new OrderSpecifier<>(Order.DESC, post.postId)
        };
    }
}
