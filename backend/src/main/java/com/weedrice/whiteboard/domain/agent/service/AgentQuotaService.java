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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentQuotaService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String ACTION_POST = "POST";
    private static final String ACTION_COMMENT = "COMMENT";
    private static final long DAILY_AGENT_POST_LIMIT = 50;
    private static final long DAILY_AGENT_COMMENT_LIMIT = 100;

    private final AgentDailyQuotaRepository agentDailyQuotaRepository;

    @Transactional
    public void reservePostCreation(Agent agent) {
        reserve(agent, ACTION_POST, DAILY_AGENT_POST_LIMIT, "Daily agent post limit exceeded");
    }

    @Transactional
    public void reserveCommentCreation(Agent agent) {
        reserve(agent, ACTION_COMMENT, DAILY_AGENT_COMMENT_LIMIT, "Daily agent comment limit exceeded");
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
                .orElseThrow(() -> new IllegalStateException("Agent daily quota row could not be locked"));
    }
}
