package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.domain.notification.web.NotificationSseEmitterRegistry;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentTopicAccessService {

    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final UserWritableResolver userWritableResolver;
    private final UserBlockService userBlockService;
    private final PostAccessPolicy postAccessPolicy;
    private final NotificationSseEmitterRegistry emitterRegistry;

    @Transactional
    public void subscribeReadable(Long userId, Long postId, String subscriberId) {
        Post initialPost = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        Long boardId = initialPost.getBoard().getBoardId();
        Long authorId = initialPost.getUser().getUserId();
        UserWritableResolver.LockedUserPair lockedUsers =
                userWritableResolver.lockUserPairForUpdate(userId, authorId);
        boardRepository.findByIdForUpdate(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        Post lockedPost = postRepository.findByIdWithRelationsForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!boardId.equals(lockedPost.getBoard().getBoardId())
                || !authorId.equals(lockedPost.getUser().getUserId())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        validateReadable(lockedUsers.firstUser(), lockedPost);
        emitterRegistry.subscribeCommentTopic(userId, boardId, postId, subscriberId);
    }

    public Long validateReadable(Long userId, Long postId) {
        User viewer = userRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, User.STATUS_ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        validateReadable(viewer, post);
        return post.getBoard().getBoardId();
    }

    private void validateReadable(User viewer, Post post) {
        if (Boolean.TRUE.equals(post.getIsBlinded())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        boolean authorBlocked = userBlockService.isEitherDirectionBlocked(
                viewer.getUserId(),
                post.getUser().getUserId());
        postAccessPolicy.validateReadable(post, viewer, authorBlocked);
    }
}
