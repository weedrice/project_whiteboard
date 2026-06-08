package com.weedrice.whiteboard.domain.sanction.service;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.service.CommentReadSupport;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.entity.ReportTargetType;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SanctionTargetResolver {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentReadSupport commentReadSupport;

    User resolveTargetUser(Long targetUserId, Long contentId, String normalizedContentType) {
        User targetUser = findSanctionTargetUser(targetUserId);
        validateSanctionContentTarget(contentId, normalizedContentType, targetUser);
        return targetUser;
    }

    private User findSanctionTargetUser(Long targetUserId) {
        if (targetUserId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        User targetUser = userRepository.findByIdForUpdate(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!targetUser.isActiveAccount()) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
        return targetUser;
    }

    private void validateSanctionContentTarget(Long contentId, String normalizedContentType, User targetUser) {
        if (contentId == null) {
            return;
        }

        ReportTargetType targetType = ReportTargetType.valueOf(normalizedContentType);
        switch (targetType) {
            case POST -> validatePostTarget(contentId, targetUser);
            case COMMENT -> validateCommentTarget(contentId, targetUser);
            case USER -> validateUserContentTarget(contentId, targetUser);
        }
    }

    private void validatePostTarget(Long postId, User targetUser) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        validateSameTarget(post.getUser(), targetUser);
    }

    private void validateCommentTarget(Long commentId, User targetUser) {
        Comment comment = commentReadSupport.getNonDeletedWithRelationsOrThrow(commentId);
        if (Boolean.TRUE.equals(comment.getPost().getIsDeleted())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        validateSameTarget(comment.getUser(), targetUser);
    }

    private void validateUserContentTarget(Long userId, User targetUser) {
        User contentUser = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!contentUser.isActiveAccount()) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
        validateSameTarget(contentUser, targetUser);
    }

    private void validateSameTarget(User contentOwner, User targetUser) {
        if (!contentOwner.isActiveAccount()) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
        if (!contentOwner.getUserId().equals(targetUser.getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
