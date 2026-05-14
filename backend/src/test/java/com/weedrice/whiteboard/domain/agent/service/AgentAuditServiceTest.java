package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AgentAuditServiceTest {

    @Mock
    private AgentAuditLogWriter agentAuditLogWriter;

    private AgentAuditService agentAuditService;

    @BeforeEach
    void setUp() {
        agentAuditService = new AgentAuditService(agentAuditLogWriter);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void saveLog_withoutTransaction_writesImmediately() {
        Agent agent = agent(7L);
        User user = user(1L);

        agentAuditService.saveLog(agent, user, AgentAuditActionType.LIKE_POST, AgentAuditTargetType.POST, 100L,
                new AgentRequestContext("127.0.0.1", "/agents/7/posts/100/like"));

        verify(agentAuditLogWriter).saveLog(7L, 1L, AgentAuditActionType.LIKE_POST, AgentAuditTargetType.POST, 100L,
                "127.0.0.1", "/agents/7/posts/100/like");
    }

    @Test
    void saveLog_withActiveTransaction_writesAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        agentAuditService.saveLog(agent(7L), user(1L), AgentAuditActionType.CREATE_POST, AgentAuditTargetType.POST,
                100L, null);

        verifyNoInteractions(agentAuditLogWriter);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCommit());

        verify(agentAuditLogWriter).saveLog(
                7L,
                1L,
                AgentAuditActionType.CREATE_POST,
                AgentAuditTargetType.POST,
                100L,
                null,
                null);
    }

    private Agent agent(Long agentId) {
        Agent agent = Agent.builder()
                .name("agent")
                .build();
        ReflectionTestUtils.setField(agent, "agentId", agentId);
        return agent;
    }

    private User user(Long userId) {
        User user = User.builder()
                .loginId("user")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }
}
