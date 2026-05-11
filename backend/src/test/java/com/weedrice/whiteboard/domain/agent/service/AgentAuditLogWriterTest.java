package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.AgentActivityLog;
import com.weedrice.whiteboard.domain.agent.repository.AgentActivityLogRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AgentAuditLogWriterTest {

    @Mock
    private AgentActivityLogRepository agentActivityLogRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private AgentAuditLogWriter agentAuditLogWriter;

    @Test
    void saveLog_trimsAndTruncatesRequestMetadataToColumnLimits() {
        String longRequestIp = " " + "1".repeat(60) + " ";
        String longRequestPath = " " + "/".repeat(300) + " ";
        ArgumentCaptor<AgentActivityLog> logCaptor = ArgumentCaptor.forClass(AgentActivityLog.class);

        agentAuditLogWriter.saveLog(null, null, "LIKE_POST", "POST", 100L, longRequestIp, longRequestPath);

        verify(agentActivityLogRepository).saveAndFlush(logCaptor.capture());
        AgentActivityLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getRequestIp()).hasSize(45);
        assertThat(savedLog.getRequestPath()).hasSize(255);
        assertThat(savedLog.getRequestIp()).doesNotStartWith(" ");
        assertThat(savedLog.getRequestPath()).doesNotStartWith(" ");
    }

    @Test
    void saveLog_preservesNullRequestMetadata() {
        ArgumentCaptor<AgentActivityLog> logCaptor = ArgumentCaptor.forClass(AgentActivityLog.class);

        agentAuditLogWriter.saveLog(null, null, "LIKE_POST", "POST", 100L, null, null);

        verify(agentActivityLogRepository).saveAndFlush(logCaptor.capture());
        AgentActivityLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getRequestIp()).isNull();
        assertThat(savedLog.getRequestPath()).isNull();
    }
}
