package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
class ReportTargetPolicy {

    private final UserBlockService userBlockService;
    private final PostAccessPolicy postAccessPolicy;

    void validatePostReportable(Post post, User reporter) {
        validateNotSelfReport(post.getUser(), reporter);
        postAccessPolicy.validateReadable(post, reporter, isEitherDirectionBlocked(reporter, post.getUser()));
    }

    void validateCommentReportable(Comment comment, User reporter) {
        postAccessPolicy.validateReadable(
                comment.getPost(),
                reporter,
                isEitherDirectionBlocked(reporter, comment.getPost().getUser()));
        validateNotSelfReport(comment.getUser(), reporter);
        if (isEitherDirectionBlocked(reporter, comment.getUser())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
    }

    void validateUserReportable(User target, User reporter) {
        if (target == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        validateNotSelfReport(target, reporter);
        if (isEitherDirectionBlocked(reporter, target)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private void validateNotSelfReport(User target, User reporter) {
        if (target != null && reporter != null && Objects.equals(reporter.getUserId(), target.getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_TARGET);
        }
    }

    private boolean isEitherDirectionBlocked(User reporter, User target) {
        if (reporter == null || target == null) {
            return false;
        }
        return userBlockService.isEitherDirectionBlocked(reporter.getUserId(), target.getUserId());
    }
}
