package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
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
import java.util.Map;
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
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final AdminRepository adminRepository;
    private final PostAccessPolicy postAccessPolicy;

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

        List<Long> uniqueMentionedUserIds = mentionedUserIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .limit(MENTION_NOTIFICATION_LIMIT)
                .toList();
        if (uniqueMentionedUserIds.isEmpty()) {
            return;
        }

        Post sourcePost = resolveSourcePost(sourceType, sourceId);
        if (sourcePost == null) {
            return;
        }

        List<User> candidates = userRepository.findAllById(uniqueMentionedUserIds).stream()
                .filter(user -> User.STATUS_ACTIVE.equals(user.getStatus()))
                .filter(user -> user.getDeletedAt() == null)
                .filter(user -> !user.getUserId().equals(actor.getUserId()))
                .toList();
        if (candidates.isEmpty()) {
            return;
        }

        List<Long> candidateUserIds = candidates.stream().map(User::getUserId).toList();
        Set<Long> blockedCandidateUserIds = Set.copyOf(
                userBlockRepository.findBlockedCandidateUserIdsEitherDirection(
                        actor.getUserId(), candidateUserIds));
        Map<Long, Set<Long>> activeAdminBoardIdsByUser = resolveActiveAdminBoardIdsByUser(
                sourcePost, candidateUserIds);
        String actorName = resolveActorName(actor, actorAgent);
        candidates.stream()
                .filter(user -> !blockedCandidateUserIds.contains(user.getUserId()))
                .filter(user -> postAccessPolicy.isReadable(
                        sourcePost,
                        user,
                        false,
                        activeAdminBoardIdsByUser.getOrDefault(user.getUserId(), Set.of())))
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

    private Map<Long, Set<Long>> resolveActiveAdminBoardIdsByUser(Post sourcePost, List<Long> candidateUserIds) {
        if (sourcePost.getBoard() == null || sourcePost.getBoard().getBoardId() == null) {
            return Map.of();
        }
        Long boardId = sourcePost.getBoard().getBoardId();
        return adminRepository.findByUserUserIdInAndIsActiveOrderByAdminIdAsc(candidateUserIds, true)
                .stream()
                .filter(admin -> admin.getUser() != null && admin.getBoard() != null)
                .filter(admin -> boardId.equals(admin.getBoard().getBoardId()))
                .collect(Collectors.groupingBy(
                        admin -> admin.getUser().getUserId(),
                        Collectors.mapping(
                                admin -> admin.getBoard().getBoardId(),
                                Collectors.toSet())));
    }

    private Post resolveSourcePost(NotificationSourceType sourceType, Long sourceId) {
        if (sourceType == NotificationSourceType.POST) {
            return postRepository.findByIdWithRelations(sourceId)
                    .orElse(null);
        }
        if (sourceType != NotificationSourceType.COMMENT) {
            return null;
        }
        return commentRepository.findNonDeletedByIdWithRelations(sourceId)
                .filter(comment -> !Boolean.TRUE.equals(comment.getIsBlinded()))
                .map(Comment::getPost)
                .orElse(null);
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
