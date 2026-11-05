package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.actor.query.ContentAuthorVisibilityRepository;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** Rechecks queued keyword discoveries at delivery time without rewriting stored history. */
@Component
@RequiredArgsConstructor
class KeywordNotificationDeliveryPolicy {
    private final PostRepository postRepository;
    private final ContentAuthorVisibilityRepository authorVisibilityRepository;
    private final UserBlockRepository userBlockRepository;

    boolean canDeliverKeyword(NotificationSourceType sourceType, Long sourceId, Long actorUserId, Long receiverUserId) {
        if (sourceType != NotificationSourceType.POST || sourceId == null || sourceId <= 0 || receiverUserId == null) {
            return false;
        }
        Post post = postRepository.findByIdWithRelations(sourceId).orElse(null);
        if (post == null || Boolean.TRUE.equals(post.getIsDeleted())
                || Boolean.TRUE.equals(post.getIsBlinded()) || Boolean.TRUE.equals(post.getIsSecret())
                || post.getBoard() == null || !Boolean.TRUE.equals(post.getBoard().getIsActive())
                || !Boolean.TRUE.equals(post.getBoard().getIsPublic())
                || !Boolean.TRUE.equals(post.getBoard().getIsListed())
                || post.getUserId() == null || !Objects.equals(post.getUserId(), actorUserId)) {
            return false;
        }
        return authorVisibilityRepository.findVisibleUserIds(List.of(post.getUserId())).contains(post.getUserId())
                && !userBlockRepository.existsEitherDirection(receiverUserId, post.getUserId());
    }
}
