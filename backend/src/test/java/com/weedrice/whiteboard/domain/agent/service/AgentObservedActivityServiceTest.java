package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.port.AgentPostActivityContentPort;
import com.weedrice.whiteboard.domain.agent.repository.AgentPostActivityReadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentObservedActivityServiceTest {
    @Test
    void reportsStoredMonotonicBoundaryInsteadOfOlderRequestedBoundary() {
        AgentOwnershipService ownership = mock(AgentOwnershipService.class);
        AgentPostActivityContentPort content = mock(AgentPostActivityContentPort.class);
        AgentPostActivityReadRepository reads = mock(AgentPostActivityReadRepository.class);
        Agent agent = Agent.builder().name("reader").status(Agent.STATUS_ACTIVE).build();
        ReflectionTestUtils.setField(agent, "agentId", 7L);
        LocalDateTime earlier = LocalDateTime.of(2026, 11, 5, 12, 0);
        LocalDateTime stored = earlier.plusMinutes(1);
        when(ownership.resolveActiveAgent(7L)).thenReturn(agent);
        when(content.resolveObservedBoundary(7L, 10L, List.of(11L))).thenReturn(earlier);
        when(reads.findReadBoundary(7L, 10L)).thenReturn(Optional.of(stored));
        when(content.countUnreadComments(7L, 10L)).thenReturn(2L);
        var response = new AgentPostActivityService(ownership, content, reads, Clock.systemUTC())
                .markObservedRead(7L, 10L, List.of(11L));
        assertThat(response.isObservedBoundaryApplied()).isTrue();
        assertThat(response.getMarkedReadAt()).isEqualTo(AgentDateTimes.toOffsetDateTime(stored));
        assertThat(response.getRemainingUnreadCount()).isEqualTo(2);
        verify(reads).upsertLastReadAt(7L, 10L, earlier);
    }
}
