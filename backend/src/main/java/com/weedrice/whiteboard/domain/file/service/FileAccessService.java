package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class FileAccessService {

    private final FileRepository fileRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostAccessPolicy postAccessPolicy;
    private final UserBlockService userBlockService;

    public File getFileForDownload(Long fileId, Long viewerUserId) {
        File file = fileRepository.findByFileIdAndStorageStatus(fileId, FileStorageStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        validateReadable(file, viewerUserId);
        return file;
    }

    private void validateReadable(File file, Long viewerUserId) {
        String relatedType = file.getRelatedType();
        if (relatedType == null && file.getRelatedId() == null) {
            validateUploader(file, viewerUserId);
            return;
        }
        if (relatedType == null || file.getRelatedId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        switch (relatedType) {
            case FileRelatedType.POST_CONTENT -> {
                Post post = postRepository.findByIdWithRelations(file.getRelatedId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
                User viewer = resolveViewer(viewerUserId);
                boolean authorBlocked = isBlockedBetweenAuthorAndViewer(post, viewer);
                postAccessPolicy.validateReadable(post, viewer, authorBlocked);
            }
            case FileRelatedType.USER_PROFILE,
                    FileRelatedType.BOARD_ICON,
                    FileRelatedType.EMOTICON_THUMBNAIL,
                    FileRelatedType.EMOTICON_IMAGE -> {
                return;
            }
            case FileRelatedType.DRAFT_POST -> validateUploader(file, viewerUserId);
            default -> throw new BusinessException(ErrorCode.FORBIDDEN);
        }
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
