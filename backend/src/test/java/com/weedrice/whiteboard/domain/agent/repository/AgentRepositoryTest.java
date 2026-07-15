package com.weedrice.whiteboard.domain.agent.repository;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class AgentRepositoryTest {

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findExpiredPendingClaimsForUpdate_returnsOnlyExpiredUnclaimedPendingAgents() {
        LocalDateTime now = LocalDateTime.now();
        Agent expiredPending = persistAgent("expired-pending", Agent.STATUS_PENDING_CLAIM, null, null, now.minusHours(25));
        persistAgent("recent-pending", Agent.STATUS_PENDING_CLAIM, null, null, now.minusHours(1));
        persistAgent("deleted-pending", Agent.STATUS_PENDING_CLAIM, null, null, now.minusHours(25)).softDelete();

        User user = persistUser();
        persistAgent("active-agent", Agent.STATUS_ACTIVE, user, now.minusHours(25), now.minusHours(25));
        persistAgent("claimed-pending", Agent.STATUS_PENDING_CLAIM, user, now.minusHours(1), now.minusHours(25));
        persistAgent("claimed-at-pending", Agent.STATUS_PENDING_CLAIM, null, now.minusHours(1), now.minusHours(25));

        entityManager.flush();
        entityManager.clear();

        List<Agent> result = agentRepository.findExpiredPendingClaimsForUpdate(
                Agent.STATUS_PENDING_CLAIM,
                now.minusHours(24));

        assertThat(result)
                .extracting(Agent::getAgentTokenHash)
                .containsExactly(expiredPending.getAgentTokenHash());
    }

    @Test
    void updateLastUsedAtIfStale_updatesOnlyNullOrStaleLastUsedAt() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 29, 12, 0);
        LocalDateTime staleBefore = now.minusMinutes(1);
        User user = persistUser();
        Agent neverUsed = persistAgent("never-used", Agent.STATUS_ACTIVE, user, null, now.minusHours(1));
        Agent stale = persistAgent("stale-used", Agent.STATUS_ACTIVE, user, null, now.minusHours(1));
        Agent recent = persistAgent("recent-used", Agent.STATUS_ACTIVE, user, null, now.minusHours(1));
        updateLastUsedAt(stale, now.minusMinutes(5));
        updateLastUsedAt(recent, now.minusSeconds(30));

        entityManager.flush();
        entityManager.clear();

        int neverUsedUpdated = agentRepository.updateLastUsedAtIfStale(neverUsed.getAgentId(), now, staleBefore);
        int staleUpdated = agentRepository.updateLastUsedAtIfStale(stale.getAgentId(), now, staleBefore);
        int recentUpdated = agentRepository.updateLastUsedAtIfStale(recent.getAgentId(), now, staleBefore);

        entityManager.flush();
        entityManager.clear();

        assertThat(neverUsedUpdated).isEqualTo(1);
        assertThat(staleUpdated).isEqualTo(1);
        assertThat(recentUpdated).isZero();
        assertThat(entityManager.find(Agent.class, neverUsed.getAgentId()).getLastUsedAt()).isEqualTo(now);
        assertThat(entityManager.find(Agent.class, stale.getAgentId()).getLastUsedAt()).isEqualTo(now);
        assertThat(entityManager.find(Agent.class, recent.getAgentId()).getLastUsedAt())
                .isEqualTo(now.minusSeconds(30));
    }

    private User persistUser() {
        User user = User.builder()
                .loginId("agent-owner")
                .password("password")
                .email("agent-owner@example.com")
                .displayName("Agent Owner")
                .build();
        entityManager.persist(user);
        return user;
    }

    private Agent persistAgent(String tokenHash, String status, User user, LocalDateTime claimedAt, LocalDateTime createdAt) {
        Agent agent = Agent.builder()
                .user(user)
                .agentTokenHash(tokenHash)
                .name(tokenHash)
                .description("desc")
                .status(status)
                .build();
        ReflectionTestUtils.setField(agent, "claimedAt", claimedAt);
        entityManager.persist(agent);
        entityManager.flush();
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE agents SET created_at = ? WHERE agent_id = ?")
                .setParameter(1, createdAt)
                .setParameter(2, agent.getAgentId())
                .executeUpdate();
        return agent;
    }

    private void updateLastUsedAt(Agent agent, LocalDateTime lastUsedAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE agents SET last_used_at = ? WHERE agent_id = ?")
                .setParameter(1, lastUsedAt)
                .setParameter(2, agent.getAgentId())
                .executeUpdate();
    }
}
