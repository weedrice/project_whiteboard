package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.entity.AgentWriteOperationId;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.agent.repository.AgentWriteOperationRepository;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Runs on H2 by default; AGENT_OPERATION_TEST_DB_URL opts into a disposable PostgreSQL database. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QuerydslConfig.class, AgentOperationService.class, AgentOperationTransactionTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AgentOperationTransactionTest {
    @Autowired AgentOperationService service;
    @Autowired AgentRepository agents;
    @MockitoSpyBean AgentWriteOperationRepository operations;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AgentOwnershipService ownership;
    @MockitoBean AgentCommandService commands;
    @MockitoBean AgentNoteService notes;
    private Long agentId;
    private String key;
    private final AgentPostCreateRequest request = new AgentPostCreateRequest();
    private final AgentRequestContext context = AgentRequestContext.empty();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("AGENT_OPERATION_TEST_DB_URL");
        if (url != null && !url.isBlank()) {
            properties.add("spring.datasource.url", () -> url);
            properties.add("spring.datasource.username", () -> System.getenv("AGENT_OPERATION_TEST_DB_USERNAME"));
            properties.add("spring.datasource.password", () -> System.getenv("AGENT_OPERATION_TEST_DB_PASSWORD"));
            properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            properties.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
            properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        }
    }

    @TestConfiguration
    static class Config {
        @Bean Clock operationClock() { return Clock.systemUTC(); }
        @Bean ObjectMapper operationMapper() { return JsonMapper.builder().build(); }
    }

    @BeforeEach
    void prepare() {
        Agent agent = agents.saveAndFlush(Agent.builder().name("test-" + UUID.randomUUID())
                .agentTokenHash(UUID.randomUUID().toString()).description("test")
                .status(Agent.STATUS_ACTIVE).build());
        agentId = agent.getAgentId();
        key = UUID.randomUUID().toString();
        jdbc.execute("CREATE TABLE IF NOT EXISTS test_operation_effects (operation_id VARCHAR(36) PRIMARY KEY)");
        when(ownership.resolveClaimedAgentForUpdate(agentId)).thenAnswer(invocation ->
                agents.findByAgentIdForUpdate(agentId).orElseThrow());
    }

    @Test
    void commandFailureRollsBackEffectAndReceiptTogether() {
        when(commands.createPost(agentId, request, context)).thenAnswer(invocation -> {
            jdbc.update("INSERT INTO test_operation_effects VALUES (?)", key);
            throw new IllegalStateException("simulated failure after content write");
        });
        assertThatThrownBy(() -> service.createPost(agentId, key, request, context))
                .isInstanceOf(IllegalStateException.class);
        assertThat(effects()).isZero();
        assertThat(operations.findById(new AgentWriteOperationId(agentId, key))).isEmpty();
    }

    @Test
    void receiptPersistenceFailureRollsBackEarlierContentEffect() {
        when(commands.createPost(agentId, request, context)).thenAnswer(invocation -> {
            jdbc.update("INSERT INTO test_operation_effects VALUES (?)", key);
            return new AgentPostCreateResponse(123L, "/posts/123");
        });
        doThrow(new org.springframework.dao.DataIntegrityViolationException("simulated receipt constraint"))
                .when(operations).save(any());
        assertThatThrownBy(() -> service.createPost(agentId, key, request, context))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(effects()).isZero();
        assertThat(operations.findById(new AgentWriteOperationId(agentId, key))).isEmpty();
    }

    @Test
    void concurrentSameKeyExecutesOnceAndBothCallersReceiveCommittedResult() throws Exception {
        CountDownLatch commandStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger();
        AtomicInteger lockCalls = new AtomicInteger();
        CountDownLatch secondLockAttempted = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (lockCalls.incrementAndGet() == 2) secondLockAttempted.countDown();
            return agents.findByAgentIdForUpdate(agentId).orElseThrow();
        }).when(ownership).resolveClaimedAgentForUpdate(agentId);
        when(commands.createPost(agentId, request, context)).thenAnswer(invocation -> {
            count.incrementAndGet();
            jdbc.update("INSERT INTO test_operation_effects VALUES (?)", key);
            commandStarted.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            return new AgentPostCreateResponse(123L, "/posts/123");
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.createPost(agentId, key, request, context));
            assertThat(commandStarted.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> service.createPost(agentId, key, request, context));
            assertThat(secondLockAttempted.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS).getPostId()).isEqualTo(123);
            assertThat(second.get(15, TimeUnit.SECONDS).getPostId()).isEqualTo(123);
        } finally {
            release.countDown();
        }
        assertThat(count).hasValue(1);
        assertThat(effects()).isEqualTo(1);
        assertThat(operations.findById(new AgentWriteOperationId(agentId, key))).isPresent();
    }

    private int effects() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM test_operation_effects WHERE operation_id = ?", Integer.class, key);
    }
}
