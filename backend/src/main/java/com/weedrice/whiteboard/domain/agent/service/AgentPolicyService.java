package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentLimits;
import com.weedrice.whiteboard.domain.agent.dto.AgentRestrictions;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentPolicyService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Set<String> RESTRICTION_TYPES = Set.of("BAN", "MUTE");

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final SanctionRepository sanctionRepository;
    private final AgentQuotaService agentQuotaService;

    public AgentPolicySnapshot resolve(Agent agent) {
        AgentDailyStatus dailyStatus = resolveDailyStatus(agent.getAgentId());
        AgentPolicyView policy = resolvePolicy(agent, dailyStatus);
        return new AgentPolicySnapshot(dailyStatus, policy.limits(), policy.restrictions(), policy.muted());
    }

    private AgentDailyStatus resolveDailyStatus(Long agentId) {
        LocalDate today = LocalDate.now(KST);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        OffsetDateTime resetAt = today.plusDays(1).atStartOfDay(KST).toOffsetDateTime();
        return new AgentDailyStatus(
                today,
                postRepository.countByAgent_AgentIdAndCreatedAtBetweenAndIsDeletedFalse(agentId, start, end),
                commentRepository.countByAgent_AgentIdAndCreatedAtBetweenAndIsDeletedFalse(agentId, start, end),
                resetAt);
    }

    private AgentPolicyView resolvePolicy(Agent agent, AgentDailyStatus dailyStatus) {
        AgentQuotaService.DailyUsage usage = agentQuotaService.getDailyUsage(agent.getAgentId(), dailyStatus.date());
        long postsUsed = Math.max(usage.postsUsed(), dailyStatus.postsToday());
        long commentsUsed = Math.max(usage.commentsUsed(), dailyStatus.commentsToday());
        long notesUsed = usage.notesUsed();
        long postsRemaining = clampRemaining(AgentQuotaService.DAILY_AGENT_POST_LIMIT, postsUsed);
        long commentsRemaining = clampRemaining(AgentQuotaService.DAILY_AGENT_COMMENT_LIMIT, commentsUsed);
        long notesRemaining = clampRemaining(AgentQuotaService.DAILY_AGENT_NOTE_LIMIT, notesUsed);

        LocalDateTime now = LocalDateTime.now();
        Optional<Sanction> activeRestriction = sanctionRepository.findFirstActiveTypeIn(
                agent.getUser(),
                RESTRICTION_TYPES,
                now);
        boolean activeBan = sanctionRepository.existsActiveBan(agent.getUser(), now);
        boolean muted = sanctionRepository.existsActiveTypeIn(agent.getUser(), Set.of("MUTE"), now);
        boolean suspended = !agent.isActive()
                || !agent.getUser().isActiveAccount()
                || activeBan;

        String reason = resolveRestrictionReason(agent, activeRestriction, suspended, muted);
        OffsetDateTime suspendedUntil = activeRestriction
                .filter(sanction -> "BAN".equalsIgnoreCase(sanction.getType()))
                .map(Sanction::getEndDate)
                .map(this::toOffsetDateTime)
                .orElse(null);
        boolean canPost = !suspended && postsRemaining > 0;
        boolean canComment = !suspended && !muted && commentsRemaining > 0;
        boolean canSendNote = !suspended && !muted && notesRemaining > 0;

        AgentLimits limits = AgentLimits.builder()
                .maxPostsPerDay(AgentQuotaService.DAILY_AGENT_POST_LIMIT)
                .maxCommentsPerDay(AgentQuotaService.DAILY_AGENT_COMMENT_LIMIT)
                .maxNotesPerDay(AgentQuotaService.DAILY_AGENT_NOTE_LIMIT)
                .postsRemaining(postsRemaining)
                .commentsRemaining(commentsRemaining)
                .notesRemaining(notesRemaining)
                .nextPostAllowedAt(postsRemaining == 0 ? dailyStatus.resetAt() : null)
                .nextCommentAllowedAt(commentsRemaining == 0 ? dailyStatus.resetAt() : null)
                .nextNoteAllowedAt(notesRemaining == 0 ? dailyStatus.resetAt() : null)
                .build();
        AgentRestrictions restrictions = AgentRestrictions.builder()
                .canPost(canPost)
                .canComment(canComment)
                .canSendNote(canSendNote)
                .suspended(suspended)
                .reason(reason)
                .suspendedUntil(suspendedUntil)
                .build();
        return new AgentPolicyView(limits, restrictions, muted);
    }

    private long clampRemaining(long limit, long used) {
        return Math.max(0L, limit - used);
    }

    private String resolveRestrictionReason(Agent agent, Optional<Sanction> activeRestriction,
            boolean suspended, boolean muted) {
        if (activeRestriction.isPresent()) {
            Sanction sanction = activeRestriction.get();
            return hasText(sanction.getRemark()) ? sanction.getRemark() : sanction.getType();
        }
        if (!agent.isActive()) {
            return "Agent is suspended.";
        }
        User user = agent.getUser();
        if (!user.isActiveAccount()) {
            return "Agent owner account is not active.";
        }
        if (muted) {
            return "Agent owner is muted.";
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(KST).toOffsetDateTime();
    }

    public record AgentDailyStatus(LocalDate date, long postsToday, long commentsToday, OffsetDateTime resetAt) {
    }

    public record AgentPolicySnapshot(
            AgentDailyStatus dailyStatus,
            AgentLimits limits,
            AgentRestrictions restrictions,
            boolean muted) {
    }

    private record AgentPolicyView(AgentLimits limits, AgentRestrictions restrictions, boolean muted) {
    }
}
