package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class PostManagerModerationService {

    private static final String DEFAULT_MANAGER_BLIND_REASON = "MANAGER";

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final BoardAccessPolicy boardAccessPolicy;
    private final Clock clock;

    public void pinPost(Long managerUserId, Long postId) {
        Post post = loadManagedPost(managerUserId, postId);
        post.pin(now());
    }

    public void unpinPost(Long managerUserId, Long postId) {
        Post post = loadManagedPost(managerUserId, postId);
        post.unpin();
    }

    public void blindPost(Long managerUserId, Long postId, String reason) {
        Post post = loadManagedPost(managerUserId, postId);
        post.blind(normalizeReason(reason), now());
    }

    public void unblindPost(Long managerUserId, Long postId) {
        Post post = loadManagedPost(managerUserId, postId);
        post.unblind();
    }

    private Post loadManagedPost(Long managerUserId, Long postId) {
        User manager = userRepository.findById(managerUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Post post = postRepository.findByIdWithRelationsForBlindUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        boardAccessPolicy.validateBoardAdmin(post.getBoard(), manager);
        return post;
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return DEFAULT_MANAGER_BLIND_REASON;
        }
        return reason.trim();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
