package com.weedrice.whiteboard.domain.comment.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.weedrice.whiteboard.domain.comment.entity.QComment.comment;

@Repository
@RequiredArgsConstructor
public class CommentRepositoryCustomImpl implements CommentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Comment> searchCommentsByKeyword(String keyword, Pageable pageable) {
        BooleanExpression keywordExpression = StringUtils.hasText(keyword) ?
                comment.content.containsIgnoreCase(keyword) : null;

        List<Comment> content = queryFactory
                .selectFrom(comment)
                .where(
                        keywordExpression,
                        comment.isDeleted.eq("N")
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(comment.createdAt.desc())
                .fetch();

        Long total = queryFactory
                .select(comment.count())
                .from(comment)
                .where(
                        keywordExpression,
                        comment.isDeleted.eq("N")
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }
}
