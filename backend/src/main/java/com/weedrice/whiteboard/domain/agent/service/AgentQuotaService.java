package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.entity.AgentDailyQuota;
import com.weedrice.whiteboard.domain.agent.repository.AgentDailyQuotaRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentQuotaService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final String ACTION_POST = "POST";
    public static final String ACTION_COMMENT = "COMMENT";
    public static final String ACTION_NOTE = "NOTE";
    public static final long DAILY_AGENT_POST_LIMIT = 50;
    public static final long DAILY_AGENT_COMMENT_LIMIT = 100;
    public static final long DAILY_AGENT_NOTE_LIMIT = 20;

    private final AgentDailyQuotaRepository agentDailyQuotaRepository;

    @Transactional
    public void reservePostCreation(Agent agent) {
        reserve(agent, ACTION_POST, DAILY_AGENT_POST_LIMIT, "Daily agent post limit exceeded");
    }

    @Transactional
    public void reserveCommentCreation(Agent agent) {
        reserve(agent, ACTION_COMMENT, DAILY_AGENT_COMMENT_LIMIT, "Daily agent comment limit exceeded");
    }

    @Transactional
    public void reserveNoteSend(Agent agent) {
        reserve(agent, ACTION_NOTE, DAILY_AGENT_NOTE_LIMIT, "Daily agent note limit exceeded");
    }

    public DailyUsage getDailyUsage(Long agentId, LocalDate quotaDate) {
        Map<String, Long> usageByActionType = agentDailyQuotaRepository
                .findUsageByAgentIdAndQuotaDateAndActionTypeIn(
                        agentId,
                        quotaDate,
                        Set.of(ACTION_POST, ACTION_COMMENT, ACTION_NOTE))
                .stream()
                .collect(Collectors.toMap(
                        AgentDailyQuotaRepository.ActionUsage::getActionType,
                        AgentDailyQuotaRepository.ActionUsage::getUsedCount,
                        Long::max));
        return new DailyUsage(
                usageByActionType.getOrDefault(ACTION_POST, 0L),
                usageByActionType.getOrDefault(ACTION_COMMENT, 0L),
                usageByActionType.getOrDefault(ACTION_NOTE, 0L));
    }

    private void reserve(Agent agent, String actionType, long limit, String message) {
        LocalDate quotaDate = LocalDate.now(KST);
        AgentDailyQuota quota = getOrCreateQuotaForUpdate(agent, quotaDate, actionType);

        if (quota.hasReachedLimit(limit)) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, message);
        }
        quota.reserve();
    }

    private AgentDailyQuota getOrCreateQuotaForUpdate(Agent agent, LocalDate quotaDate, String actionType) {
        agentDailyQuotaRepository.insertIfAbsent(agent.getAgentId(), quotaDate, actionType);
        return agentDailyQuotaRepository.findForUpdate(agent.getAgentId(), quotaDate, actionType)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    public record DailyUsage(long postsUsed, long commentsUsed, long notesUsed) {
    }
}
