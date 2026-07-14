package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.user.dto.MentionCandidateResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MentionService {

    private static final int MENTION_CANDIDATE_LIMIT = 10;
    private static final int MENTION_NOTIFICATION_LIMIT = 10;

    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final ApplicationEventPublisher eventPublisher;

    public List<MentionCandidateResponse> findCandidates(Long viewerUserId, String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.strip();
        if (normalizedKeyword.isBlank()) {
            return List.of();
        }

        List<Long> excludedUserIds = viewerUserId == null
                ? List.of()
                : userBlockRepository.findBlockedUserIdsEitherDirectionByUserId(viewerUserId);
        return userRepository.findMentionCandidates(
                        normalizedKeyword,
                        excludedUserIds.isEmpty(),
                        excludedUserIds,
                        PageRequest.of(0, MENTION_CANDIDATE_LIMIT))
                .stream()
                .map(MentionCandidateResponse::from)
                .toList();
    }

    public void publishMentions(User actor, Agent actorAgent, NotificationSourceType sourceType, Long sourceId,
            String html) {
        if (actor == null || actor.getUserId() == null || sourceType == null || sourceId == null || html == null) {
            return;
        }

        Set<Long> mentionedUserIds = extractMentionUserIds(html);
        if (mentionedUserIds.isEmpty()) {
            return;
        }

        publishMentions(actor, actorAgent, sourceType, sourceId, mentionedUserIds);
    }

    public void publishMentions(User actor, Agent actorAgent, NotificationSourceType sourceType, Long sourceId,
            Collection<Long> mentionedUserIds) {
        if (actor == null || actor.getUserId() == null || sourceType == null || sourceId == null
                || mentionedUserIds == null || mentionedUserIds.isEmpty()) {
            return;
        }

        Set<Long> uniqueMentionedUserIds = mentionedUserIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (uniqueMentionedUserIds.isEmpty()) {
            return;
        }

        String actorName = resolveActorName(actor, actorAgent);
        userRepository.findAllById(uniqueMentionedUserIds).stream()
                .filter(user -> User.STATUS_ACTIVE.equals(user.getStatus()))
                .filter(user -> user.getDeletedAt() == null)
                .filter(user -> !user.getUserId().equals(actor.getUserId()))
                .filter(user -> !userBlockRepository.existsEitherDirection(actor.getUserId(), user.getUserId()))
                .limit(MENTION_NOTIFICATION_LIMIT)
                .forEach(user -> eventPublisher.publishEvent(NotificationEvent.localized(
                        user,
                        actor,
                        actorAgent,
                        NotificationType.MENTION,
                        sourceType,
                        sourceId,
                        "notification.mention.created",
                        actorName)));
    }

    private Set<Long> extractMentionUserIds(String html) {
        Document document = Jsoup.parseBodyFragment(html);
        Set<Long> userIds = new LinkedHashSet<>();
        document.select("[data-mention-user-id]").forEach(element -> {
            if (userIds.size() >= MENTION_NOTIFICATION_LIMIT) {
                return;
            }
            try {
                userIds.add(Long.parseLong(element.attr("data-mention-user-id")));
            } catch (NumberFormatException ignored) {
                // Ignore malformed mention ids from user-generated HTML.
            }
        });
        return userIds;
    }

    private String resolveActorName(User actor, Agent actorAgent) {
        if (actorAgent != null && actorAgent.getName() != null && !actorAgent.getName().isBlank()) {
            return actorAgent.getName();
        }
        return actor.getDisplayName();
    }
}
