package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.post.entity.Post;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MentionService {

    private static final int MENTION_CANDIDATE_LIMIT = 10;
    private static final int MENTION_CANDIDATE_MIN_PREFIX_LENGTH = 2;
    private static final int MENTION_NOTIFICATION_LIMIT = 10;

    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AgentRepository agentRepository;
    private final MentionNotificationReadPolicy readPolicy;

    public void publishMentions(User actor, Long actorAgentId, NotificationSourceType sourceType, Long sourceId,
            String html) {
        publishMentionsWithAgent(actor, resolveActorAgent(actorAgentId), sourceType, sourceId, html);
    }

    public void publishMentions(User actor, Long actorAgentId, NotificationSourceType sourceType, Long sourceId,
            Collection<Long> mentionedUserIds) {
        publishMentionsWithAgent(actor, resolveActorAgent(actorAgentId), sourceType, sourceId, mentionedUserIds);
    }

    public void publishNewMentions(User actor, Long actorAgentId, NotificationSourceType sourceType, Long sourceId,
            String previousHtml, String currentHtml) {
        publishNewMentionsWithAgent(actor, resolveActorAgent(actorAgentId), sourceType, sourceId, previousHtml, currentHtml);
    }

    public void publishNewMentions(User actor, Long actorAgentId, NotificationSourceType sourceType, Long sourceId,
            Collection<Long> previousMentionedUserIds, Collection<Long> currentMentionedUserIds) {
        publishNewMentionsWithAgent(actor, resolveActorAgent(actorAgentId), sourceType, sourceId,
                previousMentionedUserIds, currentMentionedUserIds);
    }

    private Agent resolveActorAgent(Long actorAgentId) {
        return actorAgentId == null ? null : agentRepository.findById(actorAgentId).orElse(null);
    }

    public List<MentionCandidateResponse> findCandidates(Long viewerUserId, String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.strip();
        if (viewerUserId == null || normalizedKeyword.length() < MENTION_CANDIDATE_MIN_PREFIX_LENGTH) {
            return List.of();
        }

        List<Long> excludedUserIds = userBlockRepository.findBlockedUserIdsEitherDirectionByUserId(viewerUserId);
        return userRepository.findMentionCandidates(
                        normalizedKeyword,
                        excludedUserIds.isEmpty(),
                        excludedUserIds,
                        PageRequest.of(0, MENTION_CANDIDATE_LIMIT))
                .stream()
                .map(MentionCandidateResponse::from)
                .toList();
    }

    private void publishMentionsWithAgent(User actor, Agent actorAgent, NotificationSourceType sourceType, Long sourceId,
            String html) {
        if (actor == null || actor.getUserId() == null || sourceType == null || sourceId == null || html == null) {
            return;
        }

        Set<Long> mentionedUserIds = extractMentionUserIds(html);
        if (mentionedUserIds.isEmpty()) {
            return;
        }

        publishMentionsWithAgent(actor, actorAgent, sourceType, sourceId, mentionedUserIds);
    }

    private void publishMentionsWithAgent(User actor, Agent actorAgent, NotificationSourceType sourceType, Long sourceId,
            Collection<Long> mentionedUserIds) {
        if (actor == null || actor.getUserId() == null || sourceType == null || sourceId == null
                || mentionedUserIds == null || mentionedUserIds.isEmpty()) {
            return;
        }

        List<Long> uniqueMentionedUserIds = mentionedUserIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .limit(MENTION_NOTIFICATION_LIMIT)
                .toList();
        if (uniqueMentionedUserIds.isEmpty()) {
            return;
        }

        Post sourcePost = readPolicy.resolveSourcePost(sourceType, sourceId, actor.getUserId());
        if (sourcePost == null) {
            return;
        }

        List<User> candidates = readPolicy.readableRecipients(sourcePost, actor.getUserId(),
                userRepository.findAllById(uniqueMentionedUserIds));
        String actorName = resolveActorName(actor, actorAgent);
        candidates.stream()
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

    private void publishNewMentionsWithAgent(User actor, Agent actorAgent, NotificationSourceType sourceType, Long sourceId,
            String previousHtml, String currentHtml) {
        publishNewMentionsWithAgent(
                actor,
                actorAgent,
                sourceType,
                sourceId,
                extractMentionUserIds(previousHtml),
                extractMentionUserIds(currentHtml));
    }

    private void publishNewMentionsWithAgent(User actor, Agent actorAgent, NotificationSourceType sourceType, Long sourceId,
            Collection<Long> previousMentionedUserIds, Collection<Long> currentMentionedUserIds) {
        Set<Long> previousIds = normalizeMentionUserIds(previousMentionedUserIds);
        Set<Long> addedIds = normalizeMentionUserIds(currentMentionedUserIds);
        addedIds.removeAll(previousIds);
        publishMentionsWithAgent(actor, actorAgent, sourceType, sourceId, addedIds);
    }

    private Set<Long> extractMentionUserIds(String html) {
        if (html == null || html.isBlank()) {
            return new LinkedHashSet<>();
        }
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

    private Set<Long> normalizeMentionUserIds(Collection<Long> mentionedUserIds) {
        if (mentionedUserIds == null || mentionedUserIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return mentionedUserIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .limit(MENTION_NOTIFICATION_LIMIT)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String resolveActorName(User actor, Agent actorAgent) {
        if (actorAgent != null && actorAgent.getName() != null && !actorAgent.getName().isBlank()) {
            return actorAgent.getName();
        }
        return actor.getDisplayName();
    }
}
