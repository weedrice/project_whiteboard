package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CommentReadSupport {

    private final CommentRepository commentRepository;

    public Comment getNonDeletedWithRelationsOrThrow(Long commentId) {
        return commentRepository.findNonDeletedByIdWithRelations(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
    }

    public Map<Long, Long> loadVisibleReplyCounts(List<Comment> comments, Set<Long> blockedUserIds) {
        List<Long> commentIds = comments.stream()
                .map(Comment::getCommentId)
                .toList();
        if (commentIds.isEmpty()) {
            return Collections.emptyMap();
        }

        BlockedUserIdsParameter blockedUserIdsParameter = BlockedUserIdsParameter.from(blockedUserIds);
        List<CommentRepository.ReplyCountProjection> replyCounts = commentRepository.countVisibleRepliesByParentIds(
                commentIds,
                blockedUserIdsParameter.empty(),
                blockedUserIdsParameter.ids());
        if (replyCounts == null || replyCounts.isEmpty()) {
            return Collections.emptyMap();
        }

        return replyCounts.stream()
                .collect(Collectors.toMap(
                        CommentRepository.ReplyCountProjection::getParentId,
                        CommentRepository.ReplyCountProjection::getReplyCount,
                        (left, right) -> right));
    }

    public boolean isDeleted(Comment comment) {
        return Boolean.TRUE.equals(comment.getIsDeleted());
    }

    public boolean isBlockedAuthor(Comment comment, Set<Long> blockedUserIds) {
        if (comment.getUser() == null || blockedUserIds == null || blockedUserIds.isEmpty()) {
            return false;
        }
        return blockedUserIds.contains(comment.getUser().getUserId());
    }
}
