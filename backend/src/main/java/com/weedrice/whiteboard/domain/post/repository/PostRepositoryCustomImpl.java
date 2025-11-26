package com.weedrice.whiteboard.domain.post.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.tag.entity.QPostTag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.weedrice.whiteboard.domain.post.entity.QPost.post;
import static com.weedrice.whiteboard.domain.tag.entity.QPostTag.postTag;

@Repository
@RequiredArgsConstructor
public class PostRepositoryCustomImpl implements PostRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Post> findByBoardIdAndCategoryId(Long boardId, Long categoryId, Pageable pageable) {
        List<Post> content = queryFactory
                .selectFrom(post)
                .where(
                        post.board.boardId.eq(boardId),
                        categoryIdEq(categoryId),
                        post.isDeleted.eq("N")
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(post.createdAt.desc()) // Default sort
                .fetch();

        Long total = queryFactory
                .select(post.count())
                .from(post)
                .where(
                        post.board.boardId.eq(boardId),
                        categoryIdEq(categoryId),
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
                .orderBy(post.createdAt.desc())
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
                .orderBy(post.createdAt.desc())
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
}
