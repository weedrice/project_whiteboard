package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.badge.service.BadgeEvaluationService;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostLike;
import com.weedrice.whiteboard.domain.post.entity.PostLikeId;
import com.weedrice.whiteboard.domain.post.repository.PostLikeRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.service.ReactionWriter;
import com.weedrice.whiteboard.global.config.AnonymousReadCacheInvalidator;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostReactionService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ReactionWriter reactionWriter;
    private final BadgeEvaluationService badgeEvaluationService;
    private final AnonymousReadCacheInvalidator anonymousReadCacheInvalidator;

    public boolean isLikedBy(Long userId, Long postId) {
        return userId != null && postLikeRepository.existsById(new PostLikeId(userId, postId));
    }

    @Transactional
    public int like(User user, Agent actorAgent, Post post) {
        User postOwner = resolvePostOwner(post);
        Long postId = post.getPostId();
        PostLike postLike = PostLike.builder()
                .user(user)
                .post(post)
                .build();
        reactionWriter.insertOrThrowDuplicate(
                () -> postLikeRepository.saveAndFlush(postLike),
                ErrorCode.ALREADY_LIKED);

        incrementPostLikeCount(postId);
        int likeCount = getPostLikeCount(postId);
        NotificationEvent event = NotificationEvent.localized(
                postOwner,
                user,
                actorAgent,
                NotificationType.LIKE,
                NotificationSourceType.POST,
                postId,
                "notification.post.liked",
                resolveNotificationActorName(user, actorAgent));
        eventPublisher.publishEvent(event);
        badgeEvaluationService.evaluatePopularPostBadges(postOwner.getUserId(), likeCount);
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

    private User resolvePostOwner(Post post) {
        return post.getAgent() != null ? post.getAgent().getUser() : post.getUser();
    }

    private String resolveNotificationActorName(User user, Agent actorAgent) {
        if (actorAgent != null && actorAgent.getName() != null && !actorAgent.getName().isBlank()) {
            return actorAgent.getName();
        }
        return user.getDisplayName();
    }
}
