package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.file.support.FileAssociationConstraints;
import com.weedrice.whiteboard.domain.file.support.FileUrlResolver;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class FileAssociationService {

    private static final String RELATED_TYPE_POST_CONTENT = FileRelatedType.POST_CONTENT;
    private static final String RELATED_TYPE_DRAFT_POST = FileRelatedType.DRAFT_POST;
    private static final String RELATED_TYPE_USER_PROFILE = FileRelatedType.USER_PROFILE;
    private static final String RELATED_TYPE_BOARD_ICON = FileRelatedType.BOARD_ICON;

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final BoardRepository boardRepository;
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void associateFileWithEntity(Long fileId, Long ownerUserId, Long relatedId, String relatedType) {
        File file = fileRepository.findByFileIdAndStorageStatus(fileId, FileStorageStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        associateLoadedFileIfAllowed(file, ownerUserId, relatedId, relatedType);
    }

    @Transactional
    public List<String> associateFilesWithEntity(List<Long> fileIds, Long ownerUserId, Long relatedId,
            String relatedType) {
        Set<Long> orderedFileIds = normalizeFileIds(fileIds);
        if (orderedFileIds.isEmpty()) {
            return List.of();
        }

        Map<Long, File> filesById = loadActiveFilesById(orderedFileIds);
        for (Long fileId : orderedFileIds) {
            File file = filesById.get(fileId);
            if (file == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            associateLoadedFileIfAllowed(file, ownerUserId, relatedId, relatedType);
        }

        return orderedFileIds.stream()
                .map(FileUrlResolver::resolve)
                .toList();
    }

    @Transactional
    public void syncDraftFiles(List<Long> fileIds, Long ownerUserId, Long draftId) {
        Set<Long> requestedFileIds = normalizeFileIds(fileIds);
        List<File> existingDraftFiles = fileRepository.findByRelatedIdAndRelatedTypeAndStorageStatus(
                draftId,
                RELATED_TYPE_DRAFT_POST,
                FileStorageStatus.ACTIVE);

        for (File existingDraftFile : existingDraftFiles) {
            if (!requestedFileIds.contains(existingDraftFile.getFileId())) {
                existingDraftFile.markDeletionPending();
            }
        }

        associateOrMoveOwnedFiles(requestedFileIds, ownerUserId, draftId, RELATED_TYPE_DRAFT_POST, draftId);
    }

    @Transactional
    public void attachFilesToPost(List<Long> fileIds, Long ownerUserId, Long postId) {
        attachFilesToPost(fileIds, ownerUserId, postId, null);
    }

    @Transactional
    public void attachFilesToPost(List<Long> fileIds, Long ownerUserId, Long postId, Long sourceDraftId) {
        associateOrMoveOwnedFiles(
                normalizeFileIds(fileIds),
                ownerUserId,
                postId,
                RELATED_TYPE_POST_CONTENT,
                sourceDraftId);
    }

    @Transactional
    public void syncPostFiles(List<Long> fileIds, Long ownerUserId, Long postId) {
        syncPostFiles(fileIds, ownerUserId, postId, null);
    }

    @Transactional
    public void syncPostFiles(List<Long> fileIds, Long ownerUserId, Long postId, Long sourceDraftId) {
        Set<Long> requestedFileIds = normalizeFileIds(fileIds);
        List<File> existingPostFiles = fileRepository.findActiveByRelatedIdAndRelatedTypeForUpdate(
                postId,
                RELATED_TYPE_POST_CONTENT);

        for (File existingPostFile : existingPostFiles) {
            if (!requestedFileIds.contains(existingPostFile.getFileId())) {
                existingPostFile.markDeletionPending();
            }
        }

        associateOrMoveOwnedFiles(requestedFileIds, ownerUserId, postId, RELATED_TYPE_POST_CONTENT, sourceDraftId);
    }

    @Transactional
    public void markDraftFilesDeletionPending(Long draftId) {
        List<File> draftFiles = fileRepository.findByRelatedIdAndRelatedTypeAndStorageStatus(
                draftId,
                RELATED_TYPE_DRAFT_POST,
                FileStorageStatus.ACTIVE);
        for (File draftFile : draftFiles) {
            draftFile.markDeletionPending();
        }
    }

    @Transactional
    public void markPostContentFilesDeletionPending(Long postId) {
        List<File> postFiles = fileRepository.findActiveByRelatedIdAndRelatedTypeForUpdate(
                postId,
                RELATED_TYPE_POST_CONTENT);
        for (File postFile : postFiles) {
            postFile.markDeletionPending();
        }
    }

    @Transactional
    public String replaceUserProfileImage(Long profileImageId, Long ownerUserId, Long userId) {
        User lockedUser = lockUserProfileTarget(userId);
        return replaceUserProfileImageForLockedUser(profileImageId, ownerUserId, lockedUser);
    }

    @Transactional
    public String replaceUserProfileImageForLockedUser(Long profileImageId, Long ownerUserId, User lockedUser) {
        if (lockedUser == null || lockedUser.getUserId() == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        Long userId = lockedUser.getUserId();
        associateFileWithEntity(profileImageId, ownerUserId, userId, RELATED_TYPE_USER_PROFILE);

        keepOnlySelectedActiveFile(profileImageId, userId, RELATED_TYPE_USER_PROFILE);

        return FileUrlResolver.resolve(profileImageId);
    }

    @Transactional
    public String replaceBoardIcon(Long boardIconFileId, Long ownerUserId, Long boardId) {
        lockBoardIconTarget(boardId);
        associateFileWithEntity(boardIconFileId, ownerUserId, boardId, RELATED_TYPE_BOARD_ICON);

        keepOnlySelectedActiveFile(boardIconFileId, boardId, RELATED_TYPE_BOARD_ICON);

        return FileUrlResolver.resolve(boardIconFileId);
    }

    private Set<Long> normalizeFileIds(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Set.of();
        }
        if (fileIds.size() > FileAssociationConstraints.MAX_POST_FILE_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (fileIds.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return new LinkedHashSet<>(fileIds.stream()
                .toList());
    }

    private void associateOrMoveOwnedFiles(Set<Long> fileIds, Long ownerUserId, Long relatedId, String relatedType,
            Long sourceDraftId) {
        if (fileIds.isEmpty()) {
            return;
        }

        Map<Long, File> filesById = loadActiveFilesById(fileIds);
        for (Long fileId : fileIds) {
            if (!filesById.containsKey(fileId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
        }
        for (File file : filesById.values()) {
            validateOwnedFile(file, ownerUserId);
        }
        for (File file : filesById.values()) {
            if (canAssociateOrMove(file, relatedId, relatedType, sourceDraftId)) {
                continue;
            }
            throw new BusinessException(ErrorCode.FILE_ALREADY_ASSOCIATED);
        }
        for (File file : filesById.values()) {
            if (!file.isAssociatedWith(relatedId, relatedType)) {
                int updated = fileRepository.associateIfUnassociatedOrSourceDraft(
                        file.getFileId(),
                        ownerUserId,
                        relatedId,
                        relatedType,
                        RELATED_TYPE_DRAFT_POST,
                        sourceDraftId,
                        LocalDateTime.now());
                if (updated != 1) {
                    handleFailedBatchAssociation(file, ownerUserId, relatedId, relatedType);
                    continue;
                }
                file.updateRelatedInfo(relatedId, relatedType);
            }
        }
    }

    private boolean isSourceDraftFile(File file, Long sourceDraftId) {
        return sourceDraftId != null
                && RELATED_TYPE_DRAFT_POST.equals(file.getRelatedType())
                && sourceDraftId.equals(file.getRelatedId());
    }

    private boolean canAssociateOrMove(File file, Long relatedId, String relatedType, Long sourceDraftId) {
        return file.isAssociatedWith(relatedId, relatedType)
                || file.isUnassociated()
                || isSourceDraftFile(file, sourceDraftId);
    }

    private void handleFailedBatchAssociation(File file, Long ownerUserId, Long relatedId, String relatedType) {
        try {
            entityManager.refresh(file);
        } catch (EntityNotFoundException ex) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        validateActiveFile(file);
        validateOwnedFile(file, ownerUserId);
        if (file.isAssociatedWith(relatedId, relatedType)) {
            return;
        }
        throw new BusinessException(ErrorCode.FILE_ALREADY_ASSOCIATED);
    }

    private void validateOwnedFile(File file, Long ownerUserId) {
        if (file.getUploader() == null || !ownerUserId.equals(file.getUploader().getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateAssociableFile(File file) {
        if (!file.isUnassociated()) {
            throw new BusinessException(ErrorCode.FILE_ALREADY_ASSOCIATED);
        }
    }

    private void validateActiveFile(File file) {
        if (file.getStorageStatus() != null && file.getStorageStatus() != FileStorageStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    private void associateLoadedFileIfAllowed(File file, Long ownerUserId, Long relatedId, String relatedType) {
        validateOwnedFile(file, ownerUserId);
        if (file.isAssociatedWith(relatedId, relatedType)) {
            return;
        }
        validateAssociableFile(file);
        int updated = fileRepository.associateIfUnassociated(file.getFileId(), ownerUserId, relatedId, relatedType);
        if (updated == 1) {
            file.updateRelatedInfo(relatedId, relatedType);
            return;
        }
        handleFailedBatchAssociation(file, ownerUserId, relatedId, relatedType);
    }

    private Map<Long, File> loadActiveFilesById(Set<Long> fileIds) {
        List<File> files = fileRepository.findByFileIdInAndStorageStatus(
                fileIds.stream().toList(),
                FileStorageStatus.ACTIVE);
        Map<Long, File> loadedFilesById = new LinkedHashMap<>();
        for (File file : files) {
            loadedFilesById.put(file.getFileId(), file);
        }
        Map<Long, File> filesById = new LinkedHashMap<>();
        for (Long fileId : fileIds) {
            File file = loadedFilesById.get(fileId);
            if (file != null) {
                filesById.put(fileId, file);
            }
        }
        return filesById;
    }

    private User lockUserProfileTarget(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void lockBoardIconTarget(Long boardId) {
        boardRepository.findByIdForUpdate(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
    }

    private void keepOnlySelectedActiveFile(Long selectedFileId, Long relatedId, String relatedType) {
        List<File> activeFiles = fileRepository.findActiveByRelatedIdAndRelatedTypeForUpdate(relatedId, relatedType);
        for (File activeFile : activeFiles) {
            if (!selectedFileId.equals(activeFile.getFileId())) {
                activeFile.markDeletionPending();
            }
        }
    }
}
