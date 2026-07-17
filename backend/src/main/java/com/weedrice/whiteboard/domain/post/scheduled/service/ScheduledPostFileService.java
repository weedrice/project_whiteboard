package com.weedrice.whiteboard.domain.post.scheduled.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.file.support.FileAssociationConstraints;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPostFile;
import com.weedrice.whiteboard.domain.post.scheduled.repository.ScheduledPostFileRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ScheduledPostFileService {

    private static final String DRAFT_POST = "DRAFT_POST";

    private final FileRepository fileRepository;
    private final ScheduledPostFileRepository scheduledPostFileRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceReferences(Long scheduledPostId, Long ownerUserId, Long sourceDraftId, List<Long> fileIds) {
        List<Long> normalizedIds = normalize(fileIds);
        validateAndLockFiles(scheduledPostId, normalizedIds, ownerUserId, sourceDraftId);
        scheduledPostFileRepository.deleteAllByScheduledPostId(scheduledPostId);
        if (normalizedIds.isEmpty()) {
            return;
        }
        List<ScheduledPostFile> references = new ArrayList<>(normalizedIds.size());
        for (int index = 0; index < normalizedIds.size(); index++) {
            references.add(new ScheduledPostFile(scheduledPostId, normalizedIds.get(index), index + 1));
        }
        scheduledPostFileRepository.saveAll(references);
        scheduledPostFileRepository.flush();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void removeReferences(Long scheduledPostId) {
        scheduledPostFileRepository.deleteAllByScheduledPostId(scheduledPostId);
    }

    private List<Long> normalize(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        if (fileIds.size() > FileAssociationConstraints.MAX_POST_FILE_COUNT
                || fileIds.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(fileIds);
        if (uniqueIds.size() != fileIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return List.copyOf(uniqueIds);
    }

    private void validateAndLockFiles(Long scheduledPostId, List<Long> fileIds, Long ownerUserId, Long sourceDraftId) {
        for (Long fileId : fileIds.stream().sorted().toList()) {
            File file = fileRepository.findByIdForUpdate(fileId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
            if (!Objects.equals(ownerUserId, file.getUploader().getUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            if (file.getStorageStatus() != null && file.getStorageStatus() != FileStorageStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            boolean usable = file.isUnassociated()
                    || (sourceDraftId != null
                        && DRAFT_POST.equals(file.getRelatedType())
                        && sourceDraftId.equals(file.getRelatedId()));
            if (!usable) {
                throw new BusinessException(ErrorCode.FILE_ALREADY_ASSOCIATED);
            }
            if (scheduledPostFileRepository.existsByIdFileIdAndIdScheduledPostIdNot(fileId, scheduledPostId)) {
                throw new BusinessException(ErrorCode.FILE_ALREADY_ASSOCIATED);
            }
        }
    }
}
