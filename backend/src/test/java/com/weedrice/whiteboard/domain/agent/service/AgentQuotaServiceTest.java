package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.entity.AgentDailyQuota;
import com.weedrice.whiteboard.domain.agent.repository.AgentDailyQuotaRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentQuotaServiceTest {

    @Mock
    private AgentDailyQuotaRepository agentDailyQuotaRepository;

    private AgentQuotaService agentQuotaService;
    private Agent agent;

    @BeforeEach
    void setUp() {
        agentQuotaService = new AgentQuotaService(agentDailyQuotaRepository);

        User user = User.builder().loginId("user").displayName("User").build();
        agent = Agent.builder()
                .user(user)
                .agentTokenHash("hash")
                .name("agent")
                .description("desc")
                .status(Agent.STATUS_ACTIVE)
                .build();
        ReflectionTestUtils.setField(agent, "agentId", 7L);
    }

    @Test
    @DisplayName("reservePostCreation creates and increments a new quota row")
    void reservePostCreation_createsQuotaWhenAbsent() {
        when(agentDailyQuotaRepository.findForUpdate(anyLong(), any(LocalDate.class), anyString()))
                .thenReturn(Optional.empty());
        when(agentDailyQuotaRepository.saveAndFlush(any(AgentDailyQuota.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        agentQuotaService.reservePostCreation(agent);

        verify(agentDailyQuotaRepository).saveAndFlush(argThat(quota ->
                quota.getAgent().equals(agent)
                        && "POST".equals(quota.getActionType())
                        && quota.getUsedCount() == 1L
        ));
    }

    @Test
    @DisplayName("reserveCommentCreation rejects requests that exceed the daily limit")
    void reserveCommentCreation_rejectsLimitExceeded() {
        AgentDailyQuota quota = AgentDailyQuota.builder()
                .agent(agent)
                .quotaDate(LocalDate.now())
                .actionType("COMMENT")
                .usedCount(100L)
                .build();
        when(agentDailyQuotaRepository.findForUpdate(anyLong(), any(LocalDate.class), anyString()))
                .thenReturn(Optional.of(quota));

        assertThatThrownBy(() -> agentQuotaService.reserveCommentCreation(agent))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("reservePostCreation refetches quota after duplicate row creation races")
    void reservePostCreation_refetchesAfterDuplicateInsert() {
        AgentDailyQuota quota = AgentDailyQuota.builder()
                .agent(agent)
                .quotaDate(LocalDate.now())
                .actionType("POST")
                .usedCount(0L)
                .build();
        when(agentDailyQuotaRepository.findForUpdate(anyLong(), any(LocalDate.class), anyString()))
                .thenReturn(Optional.empty(), Optional.of(quota));
        when(agentDailyQuotaRepository.saveAndFlush(any(AgentDailyQuota.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        agentQuotaService.reservePostCreation(agent);

        assertThat(quota.getUsedCount()).isEqualTo(1L);
        verify(agentDailyQuotaRepository).saveAndFlush(any(AgentDailyQuota.class));
    }
}
