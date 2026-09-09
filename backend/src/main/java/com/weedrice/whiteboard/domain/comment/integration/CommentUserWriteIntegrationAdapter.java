package com.weedrice.whiteboard.domain.comment.integration;

import com.weedrice.whiteboard.domain.comment.port.CommentUserWritePort;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.service.MentionService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CommentUserWriteIntegrationAdapter implements CommentUserWritePort {

    private static final int MAX_MENTION_COUNT = 10;

    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final UserWritableResolver userWritableResolver;
    private final SanctionService sanctionService;
    private final MentionService mentionService;

    @Override
    public void validateWritable(Long userId) {
        User user = userWritableResolver.resolveForUpdate(userId);
        sanctionService.validateNotMuted(user);
    }

    @Override
    public List<Long> resolveMentionedUserIds(Long authorUserId, Collection<Long> mentionedUserIds) {
        if (mentionedUserIds == null || mentionedUserIds.isEmpty()) {
            return List.of();
        }
        Set<Long> uniqueIds = mentionedUserIds.stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (uniqueIds.isEmpty()) {
            return List.of();
        }
        List<Long> blockedIds = userBlockRepository.findBlockedUserIdsEitherDirectionByUserId(authorUserId);
        Set<Long> blockedUserIds = blockedIds == null ? Set.of() : Set.copyOf(blockedIds);
        return userRepository.findAllById(uniqueIds).stream()
                .filter(User::isActiveAccount)
                .map(User::getUserId)
                .filter(userId -> !blockedUserIds.contains(userId))
                .limit(MAX_MENTION_COUNT)
                .toList();
    }

    @Override
    public void publishMentions(Long actorUserId, Long actorAgentId, Long commentId, String content,
            Collection<Long> mentionedUserIds) {
        User actor = userRepository.findById(actorUserId).orElse(null);
        if (actor == null) {
            return;
        }
        if (mentionedUserIds != null && !mentionedUserIds.isEmpty()) {
            mentionService.publishMentions(
                    actor, actorAgentId, NotificationSourceType.COMMENT, commentId, mentionedUserIds);
        } else {
            mentionService.publishMentions(actor, actorAgentId, NotificationSourceType.COMMENT, commentId, content);
        }
    }

    @Override
    public void publishNewMentions(Long actorUserId, Long actorAgentId, Long commentId,
            Collection<Long> previousMentionedUserIds, Collection<Long> currentMentionedUserIds) {
        User actor = userRepository.findById(actorUserId).orElse(null);
        if (actor == null) {
            return;
        }
        mentionService.publishNewMentions(
                actor,
                actorAgentId,
                NotificationSourceType.COMMENT,
                commentId,
                previousMentionedUserIds,
                currentMentionedUserIds);
    }
}
