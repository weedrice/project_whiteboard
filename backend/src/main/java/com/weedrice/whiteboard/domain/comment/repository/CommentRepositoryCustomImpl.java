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
import static com.weedrice.whiteboard.domain.post.entity.QPost.post;

@Repository
@RequiredArgsConstructor
public class CommentRepositoryCustomImpl implements CommentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Comment> searchCommentsByKeyword(String keyword, List<Long> blockedUserIds, Long viewerUserId,
            Pageable pageable) {
        BooleanExpression keywordExpression = StringUtils.hasText(keyword)
                ? comment.content.containsIgnoreCase(keyword)
                : null;

        List<Comment> content = queryFactory
                .selectFrom(comment)
                .join(comment.user).fetchJoin()
                .leftJoin(comment.agent).fetchJoin()
                .join(comment.post, post).fetchJoin()
                .join(post.board).fetchJoin()
                .leftJoin(comment.parent).fetchJoin()
                .where(
                        keywordExpression,
                        comment.isDeleted.eq(false),
                        post.isDeleted.eq(false),
                        post.board.isActive.eq(true),
                        post.board.isPublic.eq(true),
                        postSecretCondition(viewerUserId),
                        notBlockedCondition(blockedUserIds)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(comment.createdAt.desc(), comment.commentId.desc())
                .fetch();

        Long total = queryFactory
                .select(comment.count())
                .from(comment)
                .join(comment.post, post)
                .where(
                        keywordExpression,
                        comment.isDeleted.eq(false),
                        post.isDeleted.eq(false),
                        post.board.isActive.eq(true),
                        post.board.isPublic.eq(true),
                        postSecretCondition(viewerUserId),
                        notBlockedCondition(blockedUserIds)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public java.util.Optional<Comment> findByIdWithRelations(@org.jspecify.annotations.NonNull Long commentId) {
        Comment result = queryFactory
                .selectFrom(comment)
                .join(comment.user).fetchJoin()
                .leftJoin(comment.agent).fetchJoin()
                .join(comment.post).fetchJoin()
                .join(comment.post.board).fetchJoin()
                .leftJoin(comment.parent).fetchJoin()
                .where(comment.commentId.eq(commentId))
                .fetchOne();
        return java.util.Optional.ofNullable(result);
    }

    @Override
    public java.util.Optional<Comment> findNonDeletedByIdWithRelations(@org.jspecify.annotations.NonNull Long commentId) {
        Comment result = queryFactory
                .selectFrom(comment)
                .join(comment.user).fetchJoin()
                .leftJoin(comment.agent).fetchJoin()
                .join(comment.post).fetchJoin()
                .join(comment.post.board).fetchJoin()
                .leftJoin(comment.parent).fetchJoin()
                .where(
                        comment.commentId.eq(commentId),
                        comment.isDeleted.eq(false))
                .fetchOne();
        return java.util.Optional.ofNullable(result);
    }

    private BooleanExpression notBlockedCondition(List<Long> blockedUserIds) {
        if (blockedUserIds == null || blockedUserIds.isEmpty()) {
            return null;
        }
        return comment.user.userId.notIn(blockedUserIds)
                .and(post.user.userId.notIn(blockedUserIds));
    }

    private BooleanExpression postSecretCondition(Long viewerUserId) {
        if (viewerUserId != null) {
            return post.isSecret.eq(false).or(post.user.userId.eq(viewerUserId));
        }
        return post.isSecret.eq(false);
    }
}
