package com.weedrice.whiteboard.domain.notification.service;

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
public class CommentTopicAccessService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final UserBlockService userBlockService;
    private final PostAccessPolicy postAccessPolicy;

    public void validateReadable(Long userId, Long postId) {
        User viewer = userRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, User.STATUS_ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (Boolean.TRUE.equals(post.getIsBlinded())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        boolean authorBlocked = userBlockService.isEitherDirectionBlocked(
                viewer.getUserId(),
                post.getUser().getUserId());
        postAccessPolicy.validateReadable(post, viewer, authorBlocked);
    }
}
