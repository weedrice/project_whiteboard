package com.weedrice.whiteboard.domain.post.repository;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weedrice.whiteboard.domain.post.entity.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static com.weedrice.whiteboard.domain.post.entity.QPost.post;
import static com.weedrice.whiteboard.domain.tag.entity.QPostTag.postTag;

@Repository
@RequiredArgsConstructor
public class PostRepositoryCustomImpl implements PostRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Post> findByBoardIdAndCategoryId(Long boardId, Long categoryId, Integer minLikes, Pageable pageable) {
        List<Post> content = queryFactory
                .selectFrom(post)
                .where(
                        post.board.boardId.eq(boardId),
                        categoryIdEq(categoryId),
                        minLikesGoe(minLikes),
                        post.isDeleted.eq("N")
                )
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
                        minLikesGoe(minLikes),
                        post.isDeleted.eq("N")
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public Page<Post> searchPostsByKeyword(String keyword, Pageable pageable) {
        BooleanExpression keywordExpression = StringUtils.hasText(keyword) ?
                post.title.containsIgnoreCase(keyword).or(post.contents.containsIgnoreCase(keyword)) : null;

        List<Post> content = queryFactory
                .selectFrom(post)
                .where(
                        keywordExpression,
                        post.isDeleted.eq("N")
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(getOrderSpecifiers(pageable))
                .fetch();

        Long total = queryFactory
                .select(post.count())
                .from(post)
                .where(
                        keywordExpression,
                        post.isDeleted.eq("N")
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public Page<Post> searchPosts(String keyword, String searchType, String boardUrl, Pageable pageable) {
        BooleanExpression searchCondition = null;
        if (StringUtils.hasText(keyword)) {
            if ("TITLE".equalsIgnoreCase(searchType)) {
                searchCondition = post.title.containsIgnoreCase(keyword);
            } else if ("CONTENT".equalsIgnoreCase(searchType)) {
                searchCondition = post.contents.containsIgnoreCase(keyword);
            } else if ("AUTHOR".equalsIgnoreCase(searchType)) {
                searchCondition = post.user.displayName.containsIgnoreCase(keyword);
            } else { // TITLE_CONTENT or default
                searchCondition = post.title.containsIgnoreCase(keyword)
                        .or(post.contents.containsIgnoreCase(keyword));
            }
        }

        BooleanExpression boardCondition = null;
        if (StringUtils.hasText(boardUrl)) {
            boardCondition = post.board.boardUrl.eq(boardUrl);
        }

        List<Post> content = queryFactory
                .selectFrom(post)
                .where(
                        searchCondition,
                        boardCondition,
                        post.isDeleted.eq("N")
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(getOrderSpecifiers(pageable))
                .fetch();

        Long total = queryFactory
                .select(post.count())
                .from(post)
                .where(
                        searchCondition,
                        boardCondition,
                        post.isDeleted.eq("N")
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public Page<Post> findByTagId(Long tagId, Pageable pageable) {
        List<Post> content = queryFactory
                .select(post)
                .from(postTag)
                .join(postTag.post, post)
                .where(
                        postTag.tag.tagId.eq(tagId),
                        post.isDeleted.eq("N")
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(getOrderSpecifiers(pageable))
                .fetch();

        Long total = queryFactory
                .select(postTag.count())
                .from(postTag)
                .where(
                        postTag.tag.tagId.eq(tagId),
                        postTag.post.isDeleted.eq("N")
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    private BooleanExpression categoryIdEq(Long categoryId) {
        return categoryId != null ? post.category.categoryId.eq(categoryId) : null;
    }

    private BooleanExpression minLikesGoe(Integer minLikes) {
        return minLikes != null ? post.likeCount.goe(minLikes) : null;
    }

    private OrderSpecifier<?>[] getOrderSpecifiers(Pageable pageable) {
        if (!pageable.getSort().isEmpty()) {
            return pageable.getSort().stream().map(order -> {
                Order direction = order.getDirection().isAscending() ? Order.ASC : Order.DESC;
                switch (order.getProperty()) {
                    case "viewCount":
                        return new OrderSpecifier<>(direction, post.viewCount);
                    case "likeCount":
                        return new OrderSpecifier<>(direction, post.likeCount);
                    case "createdAt":
                        return new OrderSpecifier<>(direction, post.createdAt);
                    case "postId":
                        return new OrderSpecifier<>(direction, post.postId);
                    default:
                        return new OrderSpecifier<>(Order.DESC, post.createdAt);
                }
            }).toArray(OrderSpecifier[]::new);
        }
        return new OrderSpecifier[]{new OrderSpecifier<>(Order.DESC, post.createdAt)};
    }
}
