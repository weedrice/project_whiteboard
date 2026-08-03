package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.post.repository.DraftPostRepository;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import com.weedrice.whiteboard.domain.post.scheduled.repository.ScheduledPostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
class PostDraftPublicationService {

    private final DraftPostRepository draftPostRepository;
    private final ScheduledPostRepository scheduledPostRepository;
    private final FileService fileService;

    DraftPost lockAndValidateForPublication(Long draftId, User user, Board targetBoard, Long targetPostId,
            Long publishingScheduledPostId) {
        if (draftId == null) {
            return null;
        }
        DraftPost draftPost = draftPostRepository.findByDraftIdAndUserForUpdate(draftId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.DRAFT_NOT_FOUND));
        if (!Objects.equals(draftPost.getBoard().getBoardId(), targetBoard.getBoardId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Long originalPostId = draftPost.getOriginalPost() != null
                ? draftPost.getOriginalPost().getPostId()
                : null;
        if (!Objects.equals(originalPostId, targetPostId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        boolean protectedBySchedule = scheduledPostRepository.existsByDraftIdAndStatusIn(
                draftId, ScheduledPost.PROTECTED_DRAFT_STATUSES);
        boolean claimedByCurrentPublication = publishingScheduledPostId != null
                && scheduledPostRepository.existsByScheduledPostIdAndDraftIdAndStatus(
                        publishingScheduledPostId, draftId, ScheduledPost.STATUS_PUBLISHING);
        if (protectedBySchedule && !claimedByCurrentPublication) {
            throw new BusinessException(ErrorCode.DRAFT_PROTECTED);
        }
        return draftPost;
    }

    void deletePublishedDraft(DraftPost draftPost) {
        if (draftPost == null) {
            return;
        }
        fileService.markDraftFilesDeletionPending(draftPost.getDraftId());
        draftPostRepository.delete(draftPost);
    }
}
