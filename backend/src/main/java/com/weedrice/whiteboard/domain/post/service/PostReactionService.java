package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.actor.ContentActorRef;
import com.weedrice.whiteboard.domain.badge.service.BadgeEvaluationService;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostLike;
import com.weedrice.whiteboard.domain.post.entity.PostLikeId;
import com.weedrice.whiteboard.domain.post.repository.PostLikeRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.port.PostLikeNotificationPort;
import com.weedrice.whiteboard.global.common.service.ReactionWriter;
import com.weedrice.whiteboard.global.config.AnonymousReadCacheInvalidator;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostReactionService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostLikeNotificationPort postLikeNotificationPort;
    private final ReactionWriter reactionWriter;
    private final BadgeEvaluationService badgeEvaluationService;
    private final AnonymousReadCacheInvalidator anonymousReadCacheInvalidator;

    public boolean isLikedBy(Long userId, Long postId) {
        return userId != null && postLikeRepository.existsById(new PostLikeId(userId, postId));
    }

    @Transactional
    public int like(ContentActorRef actor, Post post) {
        Long postId = post.getPostId();
        PostLike postLike = PostLike.builder()
                .userId(actor.ownerUserId())
                .post(post)
                .build();
        reactionWriter.insertOrThrowDuplicate(
                () -> postLikeRepository.saveAndFlush(postLike),
                ErrorCode.ALREADY_LIKED);

        incrementPostLikeCount(postId);
        int likeCount = getPostLikeCount(postId);
        postLikeNotificationPort.publishPostLiked(post.getUserId(), actor, postId);
        badgeEvaluationService.evaluatePopularPostBadges(post.getUserId(), likeCount);
        anonymousReadCacheInvalidator.evictPostEngagementCachesAfterCommit(post.getBoard().getBoardUrl());
        return likeCount;
    }

    @Transactional
    public int unlike(Long userId, Post post) {
        Long postId = post.getPostId();
        int deletedCount = postLikeRepository.deleteByUserIdAndPostId(userId, postId);
        if (deletedCount == 0) {
            throw new BusinessException(ErrorCode.NOT_LIKED);
        }

        decrementPostLikeCount(postId);
        anonymousReadCacheInvalidator.evictPostEngagementCachesAfterCommit(post.getBoard().getBoardUrl());
        return getPostLikeCount(postId);
    }

    private int getPostLikeCount(Long postId) {
        Integer likeCount = postRepository.findLikeCountByPostId(postId);
        if (likeCount == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return likeCount;
    }

    private void incrementPostLikeCount(Long postId) {
        if (postRepository.incrementLikeCount(postId) == 0) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
    }

    private void decrementPostLikeCount(Long postId) {
        if (postRepository.decrementLikeCount(postId) == 0) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
    }

}
