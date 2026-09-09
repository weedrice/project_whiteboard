package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.inquiry.legacy.InquiryLegacyWritePolicy;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
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
    private final InquiryLegacyWritePolicy inquiryLegacyWritePolicy;
    private final PostRepository postRepository;

    void validatePostReportable(Post post, User reporter) {
        validateNotSelfReport(post.getUserId(), reporter);
        postAccessPolicy.validateReadable(post, reporter, isEitherDirectionBlocked(reporter, post.getUserId()));
        inquiryLegacyWritePolicy.requireBoardWritable(post.getBoard());
    }

    void validateCommentReportable(Comment comment, User reporter) {
        if (comment == null || Boolean.TRUE.equals(comment.getIsBlinded())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        Post post = postRepository.findByIdWithRelations(comment.getPostId())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        postAccessPolicy.validateReadable(post, reporter, isEitherDirectionBlocked(reporter, post.getUserId()));
        inquiryLegacyWritePolicy.requireBoardWritable(post.getBoard());
        validateNotSelfReport(comment.getUserId(), reporter);
        if (isEitherDirectionBlocked(reporter, comment.getUserId())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
    }

    void validateUserReportable(User target, User reporter) {
        if (target == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        validateNotSelfReport(target.getUserId(), reporter);
        if (isEitherDirectionBlocked(reporter, target.getUserId())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private void validateNotSelfReport(Long targetUserId, User reporter) {
        if (targetUserId != null && reporter != null && Objects.equals(reporter.getUserId(), targetUserId)) {
            throw new BusinessException(ErrorCode.INVALID_TARGET);
        }
    }

    private boolean isEitherDirectionBlocked(User reporter, Long targetUserId) {
        if (reporter == null || targetUserId == null) {
            return false;
        }
        return userBlockService.isEitherDirectionBlocked(reporter.getUserId(), targetUserId);
    }
}
