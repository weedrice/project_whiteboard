package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.post.repository.DraftPostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PostDraftPublicationService {

    private final DraftPostRepository draftPostRepository;
    private final FileService fileService;

    void deletePublishedDraftIfOwned(Long draftId, User user) {
        if (draftId == null) {
            return;
        }
        draftPostRepository.findByDraftIdAndUserForUpdate(draftId, user)
                .ifPresent(draftPost -> deleteDraft(draftId, draftPost));
    }

    private void deleteDraft(Long draftId, DraftPost draftPost) {
        fileService.markDraftFilesDeletionPending(draftId);
        draftPostRepository.delete(draftPost);
    }
}
