package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ReportTargetValidator {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final UserBlockService userBlockService;
    private final PostAccessPolicy postAccessPolicy;

    void validate(String targetType, Long targetId, User reporter) {
        switch (targetType) {
            case "POST" -> validatePost(targetId, reporter);
            case "COMMENT" -> validateComment(targetId, reporter);
            case "USER" -> validateUser(targetId);
            default -> throw new BusinessException(ErrorCode.INVALID_TARGET,
                    "Invalid target type: " + targetType + ". Must be POST, COMMENT, or USER.");
        }
    }

    private void validatePost(Long postId, User reporter) {
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        postAccessPolicy.validateReadable(post, reporter, isAuthorBlocked(post, reporter));
    }

    private void validateComment(Long commentId, User reporter) {
        Comment comment = commentRepository.findNonDeletedByIdWithRelations(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        Post post = comment.getPost();
        postAccessPolicy.validateReadable(post, reporter, isAuthorBlocked(post, reporter));
    }

    private void validateUser(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private boolean isAuthorBlocked(Post post, User reporter) {
        if (reporter == null) {
            return false;
        }
        List<Long> blockedUserIds = userBlockService.getBlockedUserIds(reporter.getUserId());
        return blockedUserIds != null && blockedUserIds.contains(post.getUser().getUserId());
    }
}
