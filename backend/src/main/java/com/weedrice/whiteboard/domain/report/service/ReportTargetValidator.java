package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.entity.ReportTargetType;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ReportTargetValidator {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final ReportTargetPolicy reportTargetPolicy;

    void validate(String targetType, Long targetId, User reporter) {
        ReportTargetType reportTargetType;
        try {
            reportTargetType = ReportTargetType.from(targetType);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_TARGET,
                    "Invalid target type: " + targetType + ". Must be POST, COMMENT, or USER.");
        }

        switch (reportTargetType) {
            case POST -> validatePost(targetId, reporter);
            case COMMENT -> validateComment(targetId, reporter);
            case USER -> validateUser(targetId, reporter);
        }
    }

    private void validatePost(Long postId, User reporter) {
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        reportTargetPolicy.validatePostReportable(post, reporter);
    }

    private void validateComment(Long commentId, User reporter) {
        Comment comment = commentRepository.findNonDeletedByIdWithRelations(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        reportTargetPolicy.validateCommentReportable(comment, reporter);
    }

    private void validateUser(Long userId, User reporter) {
        User target = userRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, User.STATUS_ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        reportTargetPolicy.validateUserReportable(target, reporter);
    }
}
