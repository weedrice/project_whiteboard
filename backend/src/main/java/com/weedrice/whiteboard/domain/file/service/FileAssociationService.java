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

import java.time.Clock;
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
    private final Clock clock;
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void associateFileWithEntity(Long fileId, Long ownerUserId, Long relatedId, String relatedType) {
        File file = loadLockedActiveFilesById(Set.of(fileId), null, null).get(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        validateOwnedFile(file, ownerUserId);
        validateScheduledReservations(Set.of(fileId), null);
        associateLoadedFileIfAllowed(file, ownerUserId, relatedId, relatedType);
    }

    @Transactional
    public List<String> associateFilesWithEntity(List<Long> fileIds, Long ownerUserId, Long relatedId,
            String relatedType) {
        return associateFilesWithEntity(
                fileIds, ownerUserId, relatedId, relatedType, FileAssociationConstraints.MAX_POST_FILE_COUNT);
    }

    @Transactional
    public List<String> associateFilesWithEntity(List<Long> fileIds, Long ownerUserId, Long relatedId,
            String relatedType, int maxFileCount) {
        Set<Long> orderedFileIds = normalizeFileIds(fileIds, maxFileCount);
        if (orderedFileIds.isEmpty()) {
            return List.of();
        }

        Map<Long, File> filesById = loadLockedActiveFilesById(orderedFileIds, null, null);
        validateRequestedFiles(filesById, orderedFileIds, ownerUserId);
        validateScheduledReservations(orderedFileIds, null);
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
        Set<Long> requestedFileIds = normalizeFileIds(fileIds, FileAssociationConstraints.MAX_POST_FILE_COUNT);
        Map<Long, File> lockedFiles = loadLockedActiveFilesById(requestedFileIds, draftId, RELATED_TYPE_DRAFT_POST);
        associateOrMoveOwnedFiles(requestedFileIds, ownerUserId, draftId, RELATED_TYPE_DRAFT_POST, draftId,
                null, lockedFiles);
        // An omitted image may still be referenced by the live editor's undo
        // history. Keep it owned by this draft until publication or draft cleanup,
        // which finds every remaining attachment independently of draft.fileIds.
    }

    public List<Long> retainValidDraftFileIds(List<Long> fileIds, Long ownerUserId, Long draftId) {
        Set<Long> requestedFileIds = normalizeFileIds(fileIds, FileAssociationConstraints.MAX_POST_FILE_COUNT);
        if (requestedFileIds.isEmpty()) {
            return List.of();
        }
        Map<Long, File> filesById = loadActiveFilesById(requestedFileIds);
        Set<Long> reservedFileIds = Set.copyOf(fileRepository.findFileIdsReservedForOtherScheduledPost(
                requestedFileIds.stream().toList(), null));
        return requestedFileIds.stream()
                .filter(fileId -> {
                    File file = filesById.get(fileId);
                    return file != null
                            && file.getUploader() != null
                            && ownerUserId.equals(file.getUploader().getUserId())
                            && !reservedFileIds.contains(fileId)
                            && canAssociateOrMove(file, draftId, RELATED_TYPE_DRAFT_POST, draftId);
                })
                .toList();
    }

    @Transactional
    public void attachFilesToPost(List<Long> fileIds, Long ownerUserId, Long postId) {
        attachFilesToPost(fileIds, ownerUserId, postId, null);
    }

    @Transactional
    public void attachFilesToPost(List<Long> fileIds, Long ownerUserId, Long postId, Long sourceDraftId) {
        attachFilesToPost(fileIds, ownerUserId, postId, sourceDraftId, null);
    }

    @Transactional
    public void attachFilesToPost(List<Long> fileIds, Long ownerUserId, Long postId, Long sourceDraftId,
            Long publishingScheduledPostId) {
        Set<Long> requestedFileIds = normalizeFileIds(fileIds, FileAssociationConstraints.MAX_POST_FILE_COUNT);
        associateOrMoveOwnedFiles(
                requestedFileIds,
                ownerUserId,
                postId,
                RELATED_TYPE_POST_CONTENT,
                sourceDraftId,
                publishingScheduledPostId,
                loadLockedActiveFilesById(requestedFileIds, null, null));
    }

    @Transactional
    public void syncPostFiles(List<Long> fileIds, Long ownerUserId, Long postId) {
        syncPostFiles(fileIds, ownerUserId, postId, null);
    }

    @Transactional
    public void syncPostFiles(List<Long> fileIds, Long ownerUserId, Long postId, Long sourceDraftId) {
        Set<Long> requestedFileIds = normalizeFileIds(fileIds, FileAssociationConstraints.MAX_POST_FILE_COUNT);
        LocalDateTime deleteRequestedAt = now();
        Map<Long, File> lockedFiles = loadLockedActiveFilesById(requestedFileIds, postId, RELATED_TYPE_POST_CONTENT);
        associateOrMoveOwnedFiles(requestedFileIds, ownerUserId, postId, RELATED_TYPE_POST_CONTENT, sourceDraftId,
                null, lockedFiles);

        for (File existingPostFile : lockedFiles.values()) {
            if (existingPostFile.isAssociatedWith(postId, RELATED_TYPE_POST_CONTENT)
                    && !requestedFileIds.contains(existingPostFile.getFileId())) {
                existingPostFile.markDeletionPending(deleteRequestedAt);
            }
        }
    }

    @Transactional
    public void markDraftFilesDeletionPending(Long draftId) {
        List<File> draftFiles = fileRepository.findByRelatedIdAndRelatedTypeAndStorageStatus(
                draftId,
                RELATED_TYPE_DRAFT_POST,
                FileStorageStatus.ACTIVE);
        LocalDateTime deleteRequestedAt = now();
        for (File draftFile : draftFiles) {
            draftFile.markDeletionPending(deleteRequestedAt);
        }
    }

    @Transactional
    public void markPostContentFilesDeletionPending(Long postId) {
        List<File> postFiles = fileRepository.findActiveByRelatedIdAndRelatedTypeForUpdate(
                postId,
                RELATED_TYPE_POST_CONTENT);
        LocalDateTime deleteRequestedAt = now();
        for (File postFile : postFiles) {
            postFile.markDeletionPending(deleteRequestedAt);
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

    private Set<Long> normalizeFileIds(List<Long> fileIds, int maxFileCount) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Set.of();
        }
        if (fileIds.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Set<Long> normalizedFileIds = new LinkedHashSet<>(fileIds);
        if (maxFileCount < 0 || normalizedFileIds.size() > maxFileCount) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedFileIds;
    }

    private void associateOrMoveOwnedFiles(Set<Long> fileIds, Long ownerUserId, Long relatedId, String relatedType,
            Long sourceDraftId, Long publishingScheduledPostId, Map<Long, File> lockedFilesById) {
        if (fileIds.isEmpty()) {
            return;
        }

        validateRequestedFiles(lockedFilesById, fileIds, ownerUserId);
        validateScheduledReservations(fileIds, publishingScheduledPostId);
        List<File> files = fileIds.stream().map(lockedFilesById::get).toList();
        for (File file : files) {
            if (canAssociateOrMove(file, relatedId, relatedType, sourceDraftId)) {
                continue;
            }
            throw new BusinessException(ErrorCode.FILE_ALREADY_ASSOCIATED);
        }
        for (File file : files) {
            if (!file.isAssociatedWith(relatedId, relatedType)) {
                int updated = fileRepository.associateIfUnassociatedOrSourceDraft(
                        file.getFileId(),
                        ownerUserId,
                        relatedId,
                        relatedType,
                        RELATED_TYPE_DRAFT_POST,
                        sourceDraftId,
                        publishingScheduledPostId,
                        now());
                if (updated != 1) {
                    handleFailedBatchAssociation(file, ownerUserId, relatedId, relatedType);
                    continue;
                }
                file.updateRelatedInfo(relatedId, relatedType);
            }
        }
    }

    private void validateRequestedFiles(Map<Long, File> filesById, Set<Long> fileIds, Long ownerUserId) {
        for (Long fileId : fileIds) {
            if (!filesById.containsKey(fileId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
        }
        for (Long fileId : fileIds) {
            File file = filesById.get(fileId);
            validateActiveFile(file);
            validateOwnedFile(file, ownerUserId);
        }
    }

    private void validateScheduledReservations(Set<Long> fileIds, Long publishingScheduledPostId) {
        if (!fileRepository.findFileIdsReservedForOtherScheduledPost(
                fileIds.stream().toList(), publishingScheduledPostId).isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_ALREADY_ASSOCIATED);
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
        validateFileFitsRelatedType(file, relatedType);
        int updated = fileRepository.associateIfUnassociated(file.getFileId(), ownerUserId, relatedId, relatedType);
        if (updated == 1) {
            file.updateRelatedInfo(relatedId, relatedType);
            return;
        }
        handleFailedBatchAssociation(file, ownerUserId, relatedId, relatedType);
    }

    /**
     * 연결 대상별 크기·해상도 제한을 확인한다.
     *
     * <p>업로드 시점 검사는 클라이언트가 보낸 {@code target}에 의존하므로 생략하면 우회된다.
     * 여기서 쓰는 {@code relatedType}은 호출한 엔드포인트가 정하는 값이라 위조할 수 없다.
     * 이미 연결된 파일은 호출부에서 걸러지므로 과거 정책으로 저장된 파일은 영향을 받지 않는다.
     */
    private void validateFileFitsRelatedType(File file, String relatedType) {
        FileUploadTarget target = FileUploadTarget.forRelatedType(relatedType);

        if (file.getFileSize() != null && file.getFileSize() > target.getMaxSizeBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        if (!target.hasDimensionLimit()) {
            return;
        }
        Integer width = file.getImageWidth();
        Integer height = file.getImageHeight();
        if (width != null && width > target.getMaxWidth()) {
            throw new BusinessException(ErrorCode.FILE_DIMENSION_TOO_LARGE);
        }
        if (height != null && height > target.getMaxHeight()) {
            throw new BusinessException(ErrorCode.FILE_DIMENSION_TOO_LARGE);
        }
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

    private Map<Long, File> loadLockedActiveFilesById(Set<Long> fileIds, Long relatedId, String relatedType) {
        if (fileIds.isEmpty() && relatedId == null) {
            return Map.of();
        }
        // Reserve and associate under the same file locks. Read reservations in
        // a separate statement after lock acquisition so a committed reservation
        // is visible even when this transaction waited for its file lock.
        List<File> files = fileRepository.findActiveForAssociationForUpdate(
                fileIds.stream().toList(), relatedId, relatedType);
        Map<Long, File> filesById = new LinkedHashMap<>();
        for (File file : files) {
            // A draft validation may have loaded this entity before taking the
            // lock; a locking query alone does not refresh the persistence context.
            entityManager.refresh(file);
            validateActiveFile(file);
            filesById.put(file.getFileId(), file);
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
        LocalDateTime deleteRequestedAt = now();
        for (File activeFile : activeFiles) {
            if (!selectedFileId.equals(activeFile.getFileId())) {
                activeFile.markDeletionPending(deleteRequestedAt);
            }
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
