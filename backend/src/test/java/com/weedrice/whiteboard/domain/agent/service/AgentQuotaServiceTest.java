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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentQuotaServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-05-21T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private AgentDailyQuotaRepository agentDailyQuotaRepository;

    private AgentQuotaService agentQuotaService;
    private Agent agent;

    @BeforeEach
    void setUp() {
        agentQuotaService = new AgentQuotaService(agentDailyQuotaRepository, FIXED_CLOCK);

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
        AgentDailyQuota quota = AgentDailyQuota.builder()
                .agent(agent)
                .quotaDate(LocalDate.now())
                .actionType("POST")
                .usedCount(0L)
                .build();
        when(agentDailyQuotaRepository.insertIfAbsent(anyLong(), any(LocalDate.class), anyString()))
                .thenReturn(1);
        when(agentDailyQuotaRepository.findForUpdate(anyLong(), any(LocalDate.class), anyString()))
                .thenReturn(Optional.of(quota));

        agentQuotaService.reservePostCreation(agent);

        assertThat(quota.getUsedCount()).isEqualTo(1L);
        org.mockito.InOrder inOrder = inOrder(agentDailyQuotaRepository);
        inOrder.verify(agentDailyQuotaRepository).insertIfAbsent(anyLong(), any(LocalDate.class), anyString());
        inOrder.verify(agentDailyQuotaRepository).findForUpdate(anyLong(), any(LocalDate.class), anyString());
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
    @DisplayName("reservePostCreation locks quota after insert-if-absent handles duplicate row races")
    void reservePostCreation_locksQuotaAfterInsertIfAbsent() {
        AgentDailyQuota quota = AgentDailyQuota.builder()
                .agent(agent)
                .quotaDate(LocalDate.now())
                .actionType("POST")
                .usedCount(0L)
                .build();
        when(agentDailyQuotaRepository.insertIfAbsent(anyLong(), any(LocalDate.class), anyString()))
                .thenReturn(0);
        when(agentDailyQuotaRepository.findForUpdate(anyLong(), any(LocalDate.class), anyString()))
                .thenReturn(Optional.of(quota));

        agentQuotaService.reservePostCreation(agent);

        assertThat(quota.getUsedCount()).isEqualTo(1L);
        org.mockito.InOrder inOrder = inOrder(agentDailyQuotaRepository);
        inOrder.verify(agentDailyQuotaRepository).insertIfAbsent(anyLong(), any(LocalDate.class), anyString());
        inOrder.verify(agentDailyQuotaRepository).findForUpdate(anyLong(), any(LocalDate.class), anyString());
    }

    @Test
    @DisplayName("reservePostCreation maps quota lock miss to a business exception")
    void reservePostCreation_rejectsQuotaLockMissWithBusinessException() {
        when(agentDailyQuotaRepository.insertIfAbsent(anyLong(), any(LocalDate.class), anyString()))
                .thenReturn(1);
        when(agentDailyQuotaRepository.findForUpdate(anyLong(), any(LocalDate.class), anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentQuotaService.reservePostCreation(agent))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("getDailyUsage maps action usage rows and defaults missing rows to zero")
    void getDailyUsage_mapsActionUsageRows() {
        LocalDate quotaDate = LocalDate.of(2026, 5, 21);
        when(agentDailyQuotaRepository.findUsageByAgentIdAndQuotaDateAndActionTypeIn(
                anyLong(),
                any(LocalDate.class),
                quotaActionTypes()))
                .thenReturn(List.of(actionUsage("POST", 7L), actionUsage("NOTE", 3L)));

        AgentQuotaService.DailyUsage usage = agentQuotaService.getDailyUsage(7L, quotaDate);

        assertThat(usage.postsUsed()).isEqualTo(7L);
        assertThat(usage.commentsUsed()).isZero();
        assertThat(usage.notesUsed()).isEqualTo(3L);
    }

    private AgentDailyQuotaRepository.ActionUsage actionUsage(String actionType, long usedCount) {
        return new TestActionUsage(actionType, usedCount);
    }

    private Collection<String> quotaActionTypes() {
        return argThat(actionTypes -> actionTypes != null
                && actionTypes.containsAll(Set.of("POST", "COMMENT", "NOTE"))
                && actionTypes.size() == 3);
    }

    private record TestActionUsage(String actionType, long usedCount)
            implements AgentDailyQuotaRepository.ActionUsage {
        @Override
        public String getActionType() {
            return actionType;
        }

        @Override
        public long getUsedCount() {
            return usedCount;
        }
    }
}
