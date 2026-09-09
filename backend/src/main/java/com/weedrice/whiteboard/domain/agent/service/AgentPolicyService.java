package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentLimits;
import com.weedrice.whiteboard.domain.agent.dto.AgentRestrictions;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.port.AgentPolicyDataPort;
import com.weedrice.whiteboard.domain.agent.port.AgentPolicyDataPort.AgentPolicyData;
import com.weedrice.whiteboard.domain.agent.port.AgentPolicyDataPort.RestrictionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentPolicyService {

    private static final Set<String> RESTRICTION_TYPES = Set.of("BAN", "MUTE");

    private final AgentPolicyDataPort agentPolicyDataPort;
    private final AgentQuotaService agentQuotaService;
    private final Clock clock;

    public AgentPolicySnapshot resolve(Agent agent) {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        AgentPolicyData data = agentPolicyDataPort.resolve(
                agent.getAgentId(), agent.getUserId(), start, end, RESTRICTION_TYPES, LocalDateTime.now(clock));
        AgentDailyStatus dailyStatus = resolveDailyStatus(today, data);
        AgentPolicyView policy = resolvePolicy(agent, dailyStatus, data);
        return new AgentPolicySnapshot(dailyStatus, policy.limits(), policy.restrictions(), policy.muted());
    }

    private AgentDailyStatus resolveDailyStatus(LocalDate today, AgentPolicyData data) {
        OffsetDateTime resetAt = today.plusDays(1).atStartOfDay(AgentDateTimes.KST).toOffsetDateTime();
        return new AgentDailyStatus(
                today,
                data.postsToday(),
                data.commentsToday(),
                resetAt);
    }

    private AgentPolicyView resolvePolicy(Agent agent, AgentDailyStatus dailyStatus, AgentPolicyData data) {
        AgentQuotaService.DailyUsage usage = agentQuotaService.getDailyUsage(agent.getAgentId(), dailyStatus.date());
        long postsUsed = Math.max(usage.postsUsed(), dailyStatus.postsToday());
        long commentsUsed = Math.max(usage.commentsUsed(), dailyStatus.commentsToday());
        long notesUsed = usage.notesUsed();
        long postsRemaining = clampRemaining(AgentQuotaService.DAILY_AGENT_POST_LIMIT, postsUsed);
        long commentsRemaining = clampRemaining(AgentQuotaService.DAILY_AGENT_COMMENT_LIMIT, commentsUsed);
        long notesRemaining = clampRemaining(AgentQuotaService.DAILY_AGENT_NOTE_LIMIT, notesUsed);

        Optional<RestrictionSnapshot> latestActiveRestriction = data.restrictions().stream().findFirst();
        boolean activeBan = data.restrictions().stream()
                .anyMatch(sanction -> "BAN".equalsIgnoreCase(sanction.type()));
        boolean muted = data.restrictions().stream()
                .anyMatch(sanction -> "MUTE".equalsIgnoreCase(sanction.type()));
        boolean suspended = !agent.isActive()
                || !data.ownerActive()
                || activeBan;

        String reason = resolveRestrictionReason(agent, latestActiveRestriction, data.ownerActive(), muted);
        OffsetDateTime suspendedUntil = latestActiveRestriction
                .filter(sanction -> "BAN".equalsIgnoreCase(sanction.type()))
                .map(RestrictionSnapshot::endDate)
                .map(AgentDateTimes::toOffsetDateTime)
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

    private String resolveRestrictionReason(
            Agent agent,
            Optional<RestrictionSnapshot> activeRestriction,
            boolean ownerActive,
            boolean muted) {
        if (activeRestriction.isPresent()) {
            RestrictionSnapshot sanction = activeRestriction.get();
            return hasText(sanction.remark()) ? sanction.remark() : sanction.type();
        }
        if (!agent.isActive()) {
            return "Agent is suspended.";
        }
        if (!ownerActive) {
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
