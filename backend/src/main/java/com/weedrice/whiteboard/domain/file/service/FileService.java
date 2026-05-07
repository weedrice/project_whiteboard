package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonImageRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
import com.weedrice.whiteboard.domain.file.dto.FileSimpleResponse;
import com.weedrice.whiteboard.domain.file.dto.FileUploadResponse;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.file.support.FileUrlResolver;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    private static final int TEMPORARY_FILE_CLEANUP_BATCH_SIZE = 500;
    private static final int MAX_ORIGINAL_FILENAME_LENGTH = 255;

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final UserWritableResolver userWritableResolver;
    private final BoardRepository boardRepository;
    private final FileStorageService fileStorageService;
    private final TransactionTemplate transactionTemplate;
    private final EmoticonImageRepository emoticonImageRepository;
    private final EmoticonMasterRepository emoticonMasterRepository;
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FileUploadResponse uploadFile(Long uploaderId, MultipartFile multipartFile) {
        File file = processUpload(uploaderId, multipartFile);
        return FileUploadResponse.from(file);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FileSimpleResponse uploadSimpleFile(Long uploaderId, MultipartFile multipartFile) {
        File file = processUpload(uploaderId, multipartFile);
        return FileSimpleResponse.from(file);
    }

    private File processUpload(Long uploaderId, MultipartFile multipartFile) {
        if (multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        if (multipartFile.getSize() > 10 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        String declaredMimeType = multipartFile.getContentType();
        String originalFilename = normalizeOriginalFilename(multipartFile.getOriginalFilename());

        String detectedMimeType = detectImageMimeType(multipartFile);
        if (detectedMimeType == null) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }

        if (declaredMimeType != null && !isDeclaredMimeCompatible(declaredMimeType, detectedMimeType)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }

        String extension = getFileExtension(originalFilename);
        if (!isExtensionCompatible(extension, detectedMimeType)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }

        User uploader = userWritableResolver.resolve(uploaderId);
        String storedFileName = fileStorageService.storeFile(multipartFile, detectedMimeType);

        try {
            return transactionTemplate.execute(status -> {
                File file = File.builder()
                        .filePath(storedFileName)
                        .originalName(originalFilename)
                        .fileSize(multipartFile.getSize())
                        .mimeType(detectedMimeType)
                        .uploader(uploader)
                        .build();

                return fileRepository.save(file);
            });
        } catch (Exception e) {
            fileStorageService.deleteFile(storedFileName);
            throw e;
        }
    }

    private String normalizeOriginalFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "파일 이름이 올바르지 않습니다.");
        }

        String normalizedFilename = StringUtils.getFilename(StringUtils.cleanPath(originalFilename));
        if (!StringUtils.hasText(normalizedFilename)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "파일 이름이 올바르지 않습니다.");
        }
        if (normalizedFilename.length() > MAX_ORIGINAL_FILENAME_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "파일 이름은 255자를 초과할 수 없습니다.");
        }
        return normalizedFilename;
    }

    private String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex).toLowerCase();
    }

    @Transactional
    public void associateFileWithEntity(Long fileId, Long ownerUserId, Long relatedId, String relatedType) {
        File file = fileRepository.findByFileIdAndStorageStatus(fileId, FileStorageStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (file.getUploader() == null || !ownerUserId.equals(file.getUploader().getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (file.isAssociatedWith(relatedId, relatedType)) {
            return;
        }
        if (!file.isUnassociated()) {
            throw new BusinessException(ErrorCode.FILE_ALREADY_ASSOCIATED);
        }
        int updated = fileRepository.associateIfUnassociated(fileId, ownerUserId, relatedId, relatedType);
        if (updated == 1) {
            file.updateRelatedInfo(relatedId, relatedType);
            return;
        }

        entityManager.refresh(file);
        if (file.getStorageStatus() != null && file.getStorageStatus() != FileStorageStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        File current = file;
        if (current.isAssociatedWith(relatedId, relatedType)) {
            return;
        }
        throw new BusinessException(ErrorCode.FILE_ALREADY_ASSOCIATED);
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

        associateOrMoveOwnedFiles(requestedFileIds, ownerUserId, draftId, RELATED_TYPE_DRAFT_POST);
    }

    @Transactional
    public void attachFilesToPost(List<Long> fileIds, Long ownerUserId, Long postId) {
        associateOrMoveOwnedFiles(normalizeFileIds(fileIds), ownerUserId, postId, RELATED_TYPE_POST_CONTENT);
    }

    @Transactional
    public void syncPostFiles(List<Long> fileIds, Long ownerUserId, Long postId) {
        Set<Long> requestedFileIds = normalizeFileIds(fileIds);
        List<File> existingPostFiles = fileRepository.findActiveByRelatedIdAndRelatedTypeForUpdate(
                postId,
                RELATED_TYPE_POST_CONTENT);

        for (File existingPostFile : existingPostFiles) {
            if (!requestedFileIds.contains(existingPostFile.getFileId())) {
                existingPostFile.markDeletionPending();
            }
        }

        associateOrMoveOwnedFiles(requestedFileIds, ownerUserId, postId, RELATED_TYPE_POST_CONTENT);
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

    private Set<Long> normalizeFileIds(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(fileIds.stream()
                .filter(Objects::nonNull)
                .toList());
    }

    private void associateOrMoveOwnedFiles(Set<Long> fileIds, Long ownerUserId, Long relatedId, String relatedType) {
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
            if (file.getUploader() == null || !ownerUserId.equals(file.getUploader().getUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
        for (File file : filesById.values()) {
            if (file.isAssociatedWith(relatedId, relatedType)) {
                continue;
            }
            if (file.isUnassociated() || RELATED_TYPE_DRAFT_POST.equals(file.getRelatedType())) {
                continue;
            }
            throw new BusinessException(ErrorCode.FILE_ALREADY_ASSOCIATED);
        }
        for (File file : filesById.values()) {
            if (!file.isAssociatedWith(relatedId, relatedType)) {
                int updated = fileRepository.associateIfUnassociatedOrDraft(
                        file.getFileId(),
                        ownerUserId,
                        relatedId,
                        relatedType,
                        RELATED_TYPE_DRAFT_POST,
                        LocalDateTime.now());
                if (updated != 1) {
                    handleFailedBatchAssociation(file, ownerUserId, relatedId, relatedType);
                    continue;
                }
                file.updateRelatedInfo(relatedId, relatedType);
            }
        }
    }

    private void handleFailedBatchAssociation(File file, Long ownerUserId, Long relatedId, String relatedType) {
        try {
            entityManager.refresh(file);
        } catch (EntityNotFoundException ex) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (file.getStorageStatus() != null && file.getStorageStatus() != FileStorageStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (file.getUploader() == null || !ownerUserId.equals(file.getUploader().getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (file.isAssociatedWith(relatedId, relatedType)) {
            return;
        }
        throw new BusinessException(ErrorCode.FILE_ALREADY_ASSOCIATED);
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

    @Transactional
    public String replaceUserProfileImage(Long profileImageId, Long ownerUserId, Long userId) {
        lockUserProfileTarget(userId);
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

    private void lockUserProfileTarget(Long userId) {
        userRepository.findByIdForUpdate(userId)
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

    @Transactional
    public void cleanUpTemporaryFiles() {
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
        LocalDateTime deleteRequestedAt = LocalDateTime.now();
        List<Long> temporaryFileIds;
        do {
            temporaryFileIds = fileRepository.findTemporaryFileIdsForCleanup(
                    twentyFourHoursAgo,
                    PageRequest.of(0, TEMPORARY_FILE_CLEANUP_BATCH_SIZE));
            if (temporaryFileIds.isEmpty()) {
                continue;
            }
            List<Long> cleanupFileIds = excludeEmoticonReferencedFileIds(temporaryFileIds);
            if (!cleanupFileIds.isEmpty()) {
                fileRepository.requestDeletionForTemporaryFiles(cleanupFileIds, deleteRequestedAt);
            }
        } while (!temporaryFileIds.isEmpty());
    }

    private List<Long> excludeEmoticonReferencedFileIds(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }

        Map<String, Long> fileIdsByUrl = new LinkedHashMap<>();
        for (Long fileId : fileIds) {
            for (String candidateUrl : FileUrlResolver.referenceCandidates(fileId)) {
                fileIdsByUrl.put(candidateUrl, fileId);
            }
        }

        Set<Long> referencedFileIds = new LinkedHashSet<>();
        List<String> candidateUrls = fileIdsByUrl.keySet().stream().toList();
        List<String> imageUrls = emoticonImageRepository.findReferencedImageUrls(candidateUrls);
        if (imageUrls != null) {
            imageUrls.forEach(url -> referencedFileIds.add(fileIdsByUrl.get(url)));
        }
        List<String> thumbnailUrls = emoticonMasterRepository.findReferencedThumbnailUrls(candidateUrls);
        if (thumbnailUrls != null) {
            thumbnailUrls.forEach(url -> referencedFileIds.add(fileIdsByUrl.get(url)));
        }

        return fileIds.stream()
                .filter(fileId -> !referencedFileIds.contains(fileId))
                .toList();
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
        for (File file : fileRepository
                .findByRelatedIdInAndRelatedTypeAndMimeTypeStartingWithAndStorageStatusOrderByRelatedIdAscFileIdAsc(
                        relatedIds, relatedType, "image/", FileStorageStatus.ACTIVE)) {
            firstImageFileIds.putIfAbsent(file.getRelatedId(), file.getFileId());
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
        return fileRepository.findByFileIdAndStorageStatus(fileId, FileStorageStatus.ACTIVE)
                .map(file -> {
                    file.markDeletionPending();
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public boolean deleteFileWithStorageIfAssociated(Long fileId, Long relatedId, String relatedType) {
        return fileRepository.findByFileIdAndRelatedIdAndRelatedTypeAndStorageStatus(
                        fileId, relatedId, relatedType, FileStorageStatus.ACTIVE)
                .map(file -> {
                    file.markDeletionPending();
                    return true;
                })
                .orElse(false);
    }

    public List<Long> getPendingDeletionFileIds(int limit) {
        return fileRepository.findPendingDeletionCandidates(PageRequest.of(0, limit))
                .stream()
                .map(File::getFileId)
                .toList();
    }

    public List<Long> getRetryableFailedDeletionFileIds(int limit) {
        return fileRepository.findRetryableFailedDeletionCandidates(MAX_DELETE_RETRY_COUNT, PageRequest.of(0, limit))
                .stream()
                .map(File::getFileId)
                .toList();
    }

    private String detectImageMimeType(MultipartFile multipartFile) {
        try {
            byte[] data = multipartFile.getBytes();
            if (isJpeg(data)) {
                return "image/jpeg";
            }
            if (isPng(data)) {
                return "image/png";
            }
            if (isGif(data)) {
                return "image/gif";
            }
            if (isWebp(data)) {
                return "image/webp";
            }
            return null;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        }
    }

    private boolean isDeclaredMimeCompatible(String declaredMimeType, String detectedMimeType) {
        if ("image/jpg".equalsIgnoreCase(declaredMimeType) && "image/jpeg".equalsIgnoreCase(detectedMimeType)) {
            return true;
        }
        return detectedMimeType.equalsIgnoreCase(declaredMimeType);
    }

    private boolean isExtensionCompatible(String extension, String detectedMimeType) {
        return switch (detectedMimeType) {
            case "image/jpeg" -> Arrays.asList(".jpg", ".jpeg").contains(extension);
            case "image/png" -> ".png".equals(extension);
            case "image/gif" -> ".gif".equals(extension);
            case "image/webp" -> ".webp".equals(extension);
            default -> false;
        };
    }

    private boolean isJpeg(byte[] data) {
        return data.length >= 3
                && (data[0] & 0xFF) == 0xFF
                && (data[1] & 0xFF) == 0xD8
                && (data[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] data) {
        byte[] pngSignature = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        };
        if (data.length < pngSignature.length) {
            return false;
        }
        for (int i = 0; i < pngSignature.length; i++) {
            if (data[i] != pngSignature[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isGif(byte[] data) {
        return data.length >= 6
                && data[0] == 'G'
                && data[1] == 'I'
                && data[2] == 'F'
                && data[3] == '8'
                && (data[4] == '7' || data[4] == '9')
                && data[5] == 'a';
    }

    private boolean isWebp(byte[] data) {
        return data.length >= 12
                && data[0] == 'R'
                && data[1] == 'I'
                && data[2] == 'F'
                && data[3] == 'F'
                && data[8] == 'W'
                && data[9] == 'E'
                && data[10] == 'B'
                && data[11] == 'P';
    }
}
