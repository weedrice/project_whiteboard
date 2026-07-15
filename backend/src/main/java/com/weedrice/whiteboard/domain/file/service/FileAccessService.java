package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.emoticon.entity.EmoticonMaster;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.file.support.FileUrlResolver;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class FileAccessService {

    private final FileRepository fileRepository;
    private final BoardRepository boardRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final BoardAccessPolicy boardAccessPolicy;
    private final PostAccessPolicy postAccessPolicy;
    private final UserBlockService userBlockService;
    private final EmoticonMasterRepository emoticonMasterRepository;

    public File getFileForDownload(Long fileId, Long viewerUserId) {
        return getFileAccessForDownload(fileId, viewerUserId).file();
    }

    FileAccessResult getFileAccessForDownload(Long fileId, Long viewerUserId) {
        File file = fileRepository.findByFileIdAndStorageStatus(fileId, FileStorageStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        FileAccessResult.CacheScope cacheScope = validateReadable(file, viewerUserId);
        return new FileAccessResult(file, cacheScope);
    }

    private FileAccessResult.CacheScope validateReadable(File file, Long viewerUserId) {
        String relatedType = file.getRelatedType();
        if (relatedType == null && file.getRelatedId() == null) {
            Optional<FileAccessResult.CacheScope> emoticonScope = validateReferencedEmoticonIfPresent(
                    file, viewerUserId);
            if (emoticonScope.isPresent()) {
                return emoticonScope.get();
            }
            validateUploader(file, viewerUserId);
            return FileAccessResult.CacheScope.OTHER;
        }
        if (relatedType == null || file.getRelatedId() == null) {
            Optional<FileAccessResult.CacheScope> emoticonScope = validateReferencedEmoticonIfPresent(
                    file, viewerUserId);
            if (emoticonScope.isPresent()) {
                return emoticonScope.get();
            }
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return switch (relatedType) {
            case FileRelatedType.POST_CONTENT -> {
                Post post = postRepository.findByIdWithRelations(file.getRelatedId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
                User viewer = resolveViewer(viewerUserId);
                boolean authorBlocked = isBlockedBetweenAuthorAndViewer(post, viewer);
                postAccessPolicy.validateReadable(post, viewer, authorBlocked);
                yield FileAccessResult.CacheScope.OTHER;
            }
            case FileRelatedType.USER_PROFILE -> FileAccessResult.CacheScope.OTHER;
            case FileRelatedType.BOARD_ICON -> {
                validateBoardIconReadable(file, viewerUserId);
                yield FileAccessResult.CacheScope.OTHER;
            }
            case FileRelatedType.EMOTICON_THUMBNAIL,
                    FileRelatedType.EMOTICON_IMAGE -> validateEmoticonFile(file, viewerUserId);
            case FileRelatedType.DRAFT_POST -> {
                Optional<FileAccessResult.CacheScope> emoticonScope = validateReferencedEmoticonIfPresent(
                        file, viewerUserId);
                if (emoticonScope.isPresent()) {
                    yield emoticonScope.get();
                }
                validateUploader(file, viewerUserId);
                yield FileAccessResult.CacheScope.OTHER;
            }
            default -> {
                Optional<FileAccessResult.CacheScope> emoticonScope = validateReferencedEmoticonIfPresent(
                        file, viewerUserId);
                if (emoticonScope.isPresent()) {
                    yield emoticonScope.get();
                }
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        };
    }

    private void validateBoardIconReadable(File file, Long viewerUserId) {
        Board board = boardRepository.findByBoardId(file.getRelatedId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        User viewer = resolveViewer(viewerUserId);
        boardAccessPolicy.validateReadable(board, viewer);
    }

    private FileAccessResult.CacheScope validateEmoticonFile(File file, Long viewerUserId) {
        List<EmoticonMaster> masters = findEmoticonFileAccessTargets(file, file.getRelatedId());
        if (masters.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        validateEmoticonMastersReadable(masters, viewerUserId);
        return resolveEmoticonCacheScope(masters);
    }

    private Optional<FileAccessResult.CacheScope> validateReferencedEmoticonIfPresent(
            File file,
            Long viewerUserId) {
        List<EmoticonMaster> masters = findEmoticonFileAccessTargets(file, null);
        if (masters.isEmpty()) {
            return Optional.empty();
        }
        validateEmoticonMastersReadable(masters, viewerUserId);
        return Optional.of(resolveEmoticonCacheScope(masters));
    }

    private List<EmoticonMaster> findEmoticonFileAccessTargets(File file, Long emoticonId) {
        if (file.getFileId() == null) {
            return List.of();
        }
        List<String> candidateUrls = FileUrlResolver.referenceCandidates(file.getFileId());
        return emoticonMasterRepository.findFileAccessTargets(emoticonId, candidateUrls);
    }

    private void validateEmoticonMastersReadable(List<EmoticonMaster> masters, Long viewerUserId) {
        List<EmoticonMaster> inactiveMasters = masters.stream()
                .filter(master -> !isActiveEmoticon(master))
                .toList();
        if (inactiveMasters.isEmpty()) {
            return;
        }
        if (viewerUserId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (inactiveMasters.stream().allMatch(master -> master.isOwner(viewerUserId))) {
            return;
        }
        if (inactiveMasters.stream().anyMatch(master -> master.getEmoticonId() == null)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        List<Long> emoticonIds = inactiveMasters.stream()
                .map(EmoticonMaster::getEmoticonId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!emoticonIds.isEmpty()
                && emoticonMasterRepository.canUseAllEmoticons(viewerUserId, emoticonIds, emoticonIds.size())) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private boolean isActiveEmoticon(EmoticonMaster master) {
        return "Y".equals(master.getIsActive());
    }

    private FileAccessResult.CacheScope resolveEmoticonCacheScope(List<EmoticonMaster> masters) {
        boolean allActive = masters.stream().allMatch(this::isActiveEmoticon);
        return allActive
                ? FileAccessResult.CacheScope.PUBLIC_EMOTICON
                : FileAccessResult.CacheScope.RESTRICTED_EMOTICON;
    }

    private void validateUploader(File file, Long viewerUserId) {
        if (viewerUserId == null
                || file.getUploader() == null
                || !viewerUserId.equals(file.getUploader().getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private User resolveViewer(Long viewerUserId) {
        if (viewerUserId == null) {
            return null;
        }
        return userRepository.findById(viewerUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private boolean isBlockedBetweenAuthorAndViewer(Post post, User viewer) {
        if (post == null || post.getUser() == null || viewer == null) {
            return false;
        }
        Long authorUserId = post.getUser().getUserId();
        Long viewerUserId = viewer.getUserId();
        if (authorUserId == null || viewerUserId == null) {
            return false;
        }
        return userBlockService.isEitherDirectionBlocked(viewerUserId, authorUserId);
    }
}
