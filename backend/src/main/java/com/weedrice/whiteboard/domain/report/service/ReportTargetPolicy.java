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
        postAccessPolicy.validateReadable(post, reporter, isBlockedByReporter(reporter, post.getUser()));
    }

    void validateCommentReportable(Comment comment, User reporter) {
        postAccessPolicy.validateReadable(
                comment.getPost(),
                reporter,
                isBlockedByReporter(reporter, comment.getPost().getUser()));
        if (isBlockedByReporter(reporter, comment.getUser())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
    }

    void validateUserReportable(User target, User reporter) {
        if (target == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (reporter != null && Objects.equals(reporter.getUserId(), target.getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_TARGET);
        }
    }

    private boolean isBlockedByReporter(User reporter, User target) {
        if (reporter == null || target == null) {
            return false;
        }
        return userBlockService.hasBlockFromReporterToTarget(reporter.getUserId(), target.getUserId());
    }
}
