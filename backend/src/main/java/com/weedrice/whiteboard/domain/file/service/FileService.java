package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.dto.FileSimpleResponse;
import com.weedrice.whiteboard.domain.file.dto.FileUploadResponse;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.file.support.FileUrlResolver;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileService {

    public static final String RELATED_TYPE_POST_CONTENT = "POST_CONTENT";
    public static final String RELATED_TYPE_DRAFT_POST = "DRAFT_POST";
    public static final String RELATED_TYPE_USER_PROFILE = "USER_PROFILE";
    public static final String RELATED_TYPE_BOARD_ICON = "BOARD_ICON";
    private static final int MAX_DELETE_RETRY_COUNT = 5;
    private static final int TEMPORARY_FILE_CLEANUP_BATCH_SIZE = 500;

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostAccessPolicy postAccessPolicy;
    private final FileStorageService fileStorageService;
    private final TransactionTemplate transactionTemplate;
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
        String originalFilename = multipartFile.getOriginalFilename();

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

        String storedFileName = fileStorageService.storeFile(multipartFile);

        try {
            return transactionTemplate.execute(status -> {
                User uploader = userRepository.findById(uploaderId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                File file = File.builder()
                        .filePath(storedFileName)
                        .originalName(multipartFile.getOriginalFilename())
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

        for (Long fileId : requestedFileIds) {
            associateOrMoveOwnedFile(fileId, ownerUserId, draftId, RELATED_TYPE_DRAFT_POST);
        }
    }

    @Transactional
    public void attachFilesToPost(List<Long> fileIds, Long ownerUserId, Long postId) {
        for (Long fileId : normalizeFileIds(fileIds)) {
            associateOrMoveOwnedFile(fileId, ownerUserId, postId, RELATED_TYPE_POST_CONTENT);
        }
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

    private Set<Long> normalizeFileIds(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(fileIds.stream()
                .filter(Objects::nonNull)
                .toList());
    }

    private void associateOrMoveOwnedFile(Long fileId, Long ownerUserId, Long relatedId, String relatedType) {
        File file = fileRepository.findByFileIdAndStorageStatus(fileId, FileStorageStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (file.getUploader() == null || !ownerUserId.equals(file.getUploader().getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (file.isAssociatedWith(relatedId, relatedType)) {
            return;
        }
        if (file.isUnassociated() || RELATED_TYPE_DRAFT_POST.equals(file.getRelatedType())) {
            file.updateRelatedInfo(relatedId, relatedType);
            return;
        }
        throw new BusinessException(ErrorCode.FILE_ALREADY_ASSOCIATED);
    }

    @Transactional
    public String replaceUserProfileImage(Long profileImageId, Long ownerUserId, Long userId) {
        associateFileWithEntity(profileImageId, ownerUserId, userId, RELATED_TYPE_USER_PROFILE);

        List<File> profileFiles = fileRepository.findByRelatedIdAndRelatedTypeAndStorageStatus(
                userId,
                RELATED_TYPE_USER_PROFILE,
                FileStorageStatus.ACTIVE);

        for (File profileFile : profileFiles) {
            if (!profileImageId.equals(profileFile.getFileId())) {
                profileFile.markDeletionPending();
            }
        }

        return FileUrlResolver.resolve(profileImageId);
    }

    @Transactional
    public String replaceBoardIcon(Long boardIconFileId, Long ownerUserId, Long boardId) {
        associateFileWithEntity(boardIconFileId, ownerUserId, boardId, RELATED_TYPE_BOARD_ICON);

        List<File> boardIconFiles = fileRepository.findByRelatedIdAndRelatedTypeAndStorageStatus(
                boardId,
                RELATED_TYPE_BOARD_ICON,
                FileStorageStatus.ACTIVE);

        for (File boardIconFile : boardIconFiles) {
            if (!boardIconFileId.equals(boardIconFile.getFileId())) {
                boardIconFile.markDeletionPending();
            }
        }

        return FileUrlResolver.resolve(boardIconFileId);
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
            fileRepository.requestDeletionForTemporaryFiles(temporaryFileIds, deleteRequestedAt);
        } while (!temporaryFileIds.isEmpty());
    }

    public File getFileForDownload(Long fileId, Long viewerUserId) {
        File file = fileRepository.findByFileIdAndStorageStatus(fileId, FileStorageStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        validateReadable(file, viewerUserId);
        return file;
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

    private void validateReadable(File file, Long viewerUserId) {
        if (!RELATED_TYPE_POST_CONTENT.equals(file.getRelatedType()) || file.getRelatedId() == null) {
            return;
        }

        Post post = postRepository.findById(file.getRelatedId())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        postAccessPolicy.validateReadable(post, resolveViewer(viewerUserId));
    }

    private User resolveViewer(Long viewerUserId) {
        if (viewerUserId == null) {
            return null;
        }
        return userRepository.findById(viewerUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
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
