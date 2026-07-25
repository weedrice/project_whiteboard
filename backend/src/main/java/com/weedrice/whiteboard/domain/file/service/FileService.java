package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.dto.FileSimpleResponse;
import com.weedrice.whiteboard.domain.file.dto.FileUploadResponse;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.file.repository.FileRepository.FirstImageFileIdProjection;
import com.weedrice.whiteboard.domain.file.support.FileUrlResolver;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileService {

    public static final String RELATED_TYPE_POST_CONTENT = FileRelatedType.POST_CONTENT;
    public static final String RELATED_TYPE_DRAFT_POST = FileRelatedType.DRAFT_POST;
    public static final String RELATED_TYPE_USER_PROFILE = FileRelatedType.USER_PROFILE;
    public static final String RELATED_TYPE_BOARD_ICON = FileRelatedType.BOARD_ICON;
    public static final String RELATED_TYPE_EMOTICON_THUMBNAIL = FileRelatedType.EMOTICON_THUMBNAIL;
    public static final String RELATED_TYPE_EMOTICON_IMAGE = FileRelatedType.EMOTICON_IMAGE;
    private static final int MAX_DELETE_RETRY_COUNT = 5;
    private static final int DELETE_CLAIM_STALE_MINUTES = 30;
    private static final int TEMPORARY_FILE_CLEANUP_BATCH_SIZE = 500;

    private final FileUploadService fileUploadService;
    private final FileAssociationService fileAssociationService;
    private final FileRepository fileRepository;
    private final FileTemporaryCleanupWorker fileTemporaryCleanupWorker;
    private final Clock clock;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FileUploadResponse uploadFile(Long uploaderId, MultipartFile multipartFile) {
        return uploadFile(uploaderId, multipartFile, FileUploadTarget.GENERIC);
    }

    public FileUploadResponse uploadFile(Long uploaderId, MultipartFile multipartFile, FileUploadTarget target) {
        return fileUploadService.uploadFile(uploaderId, multipartFile, target);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FileSimpleResponse uploadSimpleFile(Long uploaderId, MultipartFile multipartFile) {
        return uploadSimpleFile(uploaderId, multipartFile, FileUploadTarget.GENERIC);
    }

    public FileSimpleResponse uploadSimpleFile(Long uploaderId, MultipartFile multipartFile,
            FileUploadTarget target) {
        return fileUploadService.uploadSimpleFile(uploaderId, multipartFile, target);
    }

    @Transactional
    public void associateFileWithEntity(Long fileId, Long ownerUserId, Long relatedId, String relatedType) {
        fileAssociationService.associateFileWithEntity(fileId, ownerUserId, relatedId, relatedType);
    }

    @Transactional
    public List<String> associateFilesWithEntity(List<Long> fileIds, Long ownerUserId, Long relatedId,
            String relatedType) {
        return fileAssociationService.associateFilesWithEntity(fileIds, ownerUserId, relatedId, relatedType);
    }

    @Transactional
    public List<String> associateFilesWithEntity(List<Long> fileIds, Long ownerUserId, Long relatedId,
            String relatedType, int maxFileCount) {
        return fileAssociationService.associateFilesWithEntity(
                fileIds, ownerUserId, relatedId, relatedType, maxFileCount);
    }

    @Transactional
    public void syncDraftFiles(List<Long> fileIds, Long ownerUserId, Long draftId) {
        fileAssociationService.syncDraftFiles(fileIds, ownerUserId, draftId);
    }

    @Transactional
    public void attachFilesToPost(List<Long> fileIds, Long ownerUserId, Long postId) {
        fileAssociationService.attachFilesToPost(fileIds, ownerUserId, postId);
    }

    @Transactional
    public void attachFilesToPost(List<Long> fileIds, Long ownerUserId, Long postId, Long sourceDraftId) {
        fileAssociationService.attachFilesToPost(fileIds, ownerUserId, postId, sourceDraftId);
    }

    @Transactional
    public void syncPostFiles(List<Long> fileIds, Long ownerUserId, Long postId) {
        fileAssociationService.syncPostFiles(fileIds, ownerUserId, postId);
    }

    @Transactional
    public void syncPostFiles(List<Long> fileIds, Long ownerUserId, Long postId, Long sourceDraftId) {
        fileAssociationService.syncPostFiles(fileIds, ownerUserId, postId, sourceDraftId);
    }

    @Transactional
    public void markDraftFilesDeletionPending(Long draftId) {
        fileAssociationService.markDraftFilesDeletionPending(draftId);
    }

    @Transactional
    public void markPostContentFilesDeletionPending(Long postId) {
        fileAssociationService.markPostContentFilesDeletionPending(postId);
    }

    @Transactional
    public String replaceUserProfileImage(Long profileImageId, Long ownerUserId, Long userId) {
        return fileAssociationService.replaceUserProfileImage(profileImageId, ownerUserId, userId);
    }

    @Transactional
    public String replaceUserProfileImageForLockedUser(Long profileImageId, Long ownerUserId, User lockedUser) {
        return fileAssociationService.replaceUserProfileImageForLockedUser(profileImageId, ownerUserId, lockedUser);
    }

    @Transactional
    public String replaceBoardIcon(Long boardIconFileId, Long ownerUserId, Long boardId) {
        return fileAssociationService.replaceBoardIcon(boardIconFileId, ownerUserId, boardId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void cleanUpTemporaryFiles() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime twentyFourHoursAgo = now.minusHours(24);
        LocalDateTime deleteRequestedAt = now;
        fileTemporaryCleanupWorker.requestPendingUploadDeletion(twentyFourHoursAgo, deleteRequestedAt);
        LocalDateTime lastCreatedAt = null;
        Long lastFileId = null;
        do {
            FileTemporaryCleanupWorker.CleanupBatchResult result = fileTemporaryCleanupWorker.requestDeletionBatch(
                    twentyFourHoursAgo,
                    lastCreatedAt,
                    lastFileId,
                    TEMPORARY_FILE_CLEANUP_BATCH_SIZE,
                    deleteRequestedAt);
            if (result.finished()) {
                break;
            }
            lastCreatedAt = result.lastCreatedAt();
            lastFileId = result.lastFileId();
        } while (true);
    }

    public List<File> getFilesByRelatedEntity(Long relatedId, String relatedType) {
        return fileRepository.findByRelatedIdAndRelatedTypeAndStorageStatus(relatedId, relatedType,
                FileStorageStatus.ACTIVE);
    }

    public List<File> getFilesByRelatedEntityIn(List<Long> relatedIds, String relatedType) {
        return fileRepository.findByRelatedIdInAndRelatedTypeAndStorageStatus(relatedIds, relatedType,
                FileStorageStatus.ACTIVE);
    }

    public List<Long> getRelatedIdsWithImages(List<Long> relatedIds, String relatedType) {
        return fileRepository.findRelatedIdsWithImages(relatedIds, relatedType, FileStorageStatus.ACTIVE);
    }

    public Map<Long, Long> getFirstImageFileIdsByRelatedIds(List<Long> relatedIds, String relatedType) {
        if (relatedIds == null || relatedIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Long> firstImageFileIds = new LinkedHashMap<>();
        for (FirstImageFileIdProjection fileIdProjection : fileRepository
                .findFirstImageFileIdsByRelatedIdsAndRelatedTypeAndMimeTypeStartingWithAndStorageStatus(
                        relatedIds, relatedType, "image/", FileStorageStatus.ACTIVE)) {
            firstImageFileIds.put(fileIdProjection.getRelatedId(), fileIdProjection.getFileId());
        }
        return firstImageFileIds;
    }

    public Map<Long, Long> getFirstImageFileIdsForPosts(List<Long> postIds) {
        return getFirstImageFileIdsByRelatedIds(postIds, RELATED_TYPE_POST_CONTENT);
    }

    public Long getOneImageFileIdForPost(Long postId) {
        return fileRepository
                .findFirstByRelatedIdAndRelatedTypeAndMimeTypeStartingWithAndStorageStatus(
                        postId, RELATED_TYPE_POST_CONTENT, "image/", FileStorageStatus.ACTIVE)
                .map(File::getFileId)
                .orElse(null);
    }

    public static Long extractFileIdFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("/files/(\\d+)(?:\\?|$|/)");
        java.util.regex.Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    @Transactional
    public boolean deleteFileWithStorage(Long fileId) {
        LocalDateTime deleteRequestedAt = LocalDateTime.now(clock);
        return fileRepository.findByFileIdAndStorageStatus(fileId, FileStorageStatus.ACTIVE)
                .map(file -> {
                    file.markDeletionPending(deleteRequestedAt);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public boolean deleteFileWithStorageIfAssociated(Long fileId, Long relatedId, String relatedType) {
        LocalDateTime deleteRequestedAt = LocalDateTime.now(clock);
        return fileRepository.findByFileIdAndRelatedIdAndRelatedTypeAndStorageStatus(
                        fileId, relatedId, relatedType, FileStorageStatus.ACTIVE)
                .map(file -> {
                    file.markDeletionPending(deleteRequestedAt);
                    return true;
                })
                .orElse(false);
    }

    public List<Long> getPendingDeletionFileIds(int limit) {
        LocalDateTime staleBefore = LocalDateTime.now(clock).minusMinutes(DELETE_CLAIM_STALE_MINUTES);
        return fileRepository.findPendingDeletionFileIds(staleBefore, PageRequest.of(0, limit));
    }

    public List<Long> getRetryableFailedDeletionFileIds(int limit) {
        return fileRepository.findRetryableFailedDeletionFileIds(MAX_DELETE_RETRY_COUNT, PageRequest.of(0, limit));
    }

}
