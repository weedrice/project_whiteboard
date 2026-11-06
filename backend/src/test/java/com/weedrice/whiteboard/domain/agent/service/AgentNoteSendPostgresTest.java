package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.*;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.integration.AgentOwnerIntegrationAdapter;
import com.weedrice.whiteboard.domain.agent.integration.AgentUserRelationshipIntegrationAdapter;
import com.weedrice.whiteboard.domain.agent.port.AgentOwnerPort;
import com.weedrice.whiteboard.domain.agent.repository.*;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.domain.user.service.*;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionPolicyService;
import com.weedrice.whiteboard.domain.notification.service.NotificationAccessInvalidationService;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Dedicated disposable database only; shares no application data. */
@DataJpaTest
@Import(QuerydslConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "AGENT_NOTE_TEST_JDBC_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/agent_note_test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AgentNoteSendPostgresTest {
    @Autowired private AgentRepository agents;
    @Autowired private AgentNoteRepository notes;
    @Autowired private AgentNoteThreadRepository threads;
    @Autowired private AgentDailyQuotaRepository dailyQuotas;
    @Autowired private UserRepository users;
    @Autowired private UserBlockRepository blocks;
    @Autowired private TestEntityManager em;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    private final Clock clock = Clock.system(ZoneId.of("Asia/Seoul"));

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("AGENT_NOTE_TEST_JDBC_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("AGENT_NOTE_TEST_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("AGENT_NOTE_TEST_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Test
    void blockCommittedWhileSendWaitsRejectsNoteWithoutConsumingQuota() throws Exception {
        var fixture = fixture();
        var pid = new CompletableFuture<Integer>();
        var identified = new CompletableFuture<Void>();
        var result = new CompletableFuture<Object>();
        var executor = Executors.newSingleThreadExecutor();
        try {
            tx().executeWithoutResult(status -> {
                writableUsers().lockUserPairForUpdate(fixture.sender().getUserId(), fixture.receiver().getUserId());
                executor.submit(() -> sendAsync(fixture.sender(), fixture.receiver(), pid, identified, result));
                await(identified);
                assertWaiting(await(pid));
                blockService().blockUser(fixture.receiver().getUserId(), fixture.sender().getUserId());
            });
            assertThat(await(result)).isInstanceOf(com.weedrice.whiteboard.domain.agent.exception.AgentWriteException.class)
                    .hasFieldOrPropertyWithValue("code", "note_send_forbidden");
            assertNoNoteOrQuota(fixture);
        } finally { stop(executor); }
    }

    @Test
    void receiverDeletedWhileSendWaitsCannotBeUsedFromThePersistenceContext() throws Exception {
        var fixture = fixture();
        var pid = new CompletableFuture<Integer>();
        var identified = new CompletableFuture<Void>();
        var result = new CompletableFuture<Object>();
        var executor = Executors.newSingleThreadExecutor();
        try {
            tx().executeWithoutResult(status -> {
                writableUsers().lockUserPairForUpdate(fixture.sender().getUserId(), fixture.receiver().getUserId());
                executor.submit(() -> sendAsync(fixture.sender(), fixture.receiver(), pid, identified, result));
                await(identified);
                assertWaiting(await(pid));
                agents.findByAgentIdForUpdate(fixture.receiver().getAgentId()).orElseThrow().softDelete();
            });
            assertThat(await(result)).isInstanceOf(com.weedrice.whiteboard.global.exception.BusinessException.class);
            assertNoNoteOrQuota(fixture);
        } finally { stop(executor); }
    }

    @Test
    void reciprocalSendsUseTheSameUserAndAgentOrderWithoutDeadlock() throws Exception {
        var fixture = fixture();
        var ready = new CountDownLatch(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> tx().execute(status -> service(() -> {
                ready.countDown(); awaitLatch(ready);
            }).sendNote(fixture.sender().getAgentId(), request(fixture.receiver()), null)));
            var second = executor.submit(() -> tx().execute(status -> service(() -> {
                ready.countDown(); awaitLatch(ready);
            }).sendNote(fixture.receiver().getAgentId(), request(fixture.sender()), null)));
            var firstResult = first.get(20, TimeUnit.SECONDS);
            var secondResult = second.get(20, TimeUnit.SECONDS);
            assertThat(firstResult.getNoteThreadId()).isEqualTo(secondResult.getNoteThreadId());
            tx().executeWithoutResult(status -> {
                assertThat(notes.countUnreadNotes(fixture.sender().getAgentId())).isEqualTo(1);
                assertThat(notes.countUnreadNotes(fixture.receiver().getAgentId())).isEqualTo(1);
            });
        } finally { stop(executor); }
    }

    private void sendAsync(Agent sender, Agent receiver, CompletableFuture<Integer> pid,
            CompletableFuture<Void> identified, CompletableFuture<Object> result) {
        try {
            result.complete(tx().execute(status -> {
                pid.complete(((Number) em.getEntityManager().createNativeQuery("SELECT pg_backend_pid()")
                        .getSingleResult()).intValue());
                return service(() -> identified.complete(null)).sendNote(sender.getAgentId(), request(receiver), null);
            }));
        } catch (Throwable error) { result.complete(error); }
    }

    private void assertNoNoteOrQuota(Fixture fixture) {
        tx().executeWithoutResult(status -> {
            assertThat(notes.countUnreadNotes(fixture.receiver().getAgentId())).isZero();
            assertThat(new AgentQuotaService(dailyQuotas, clock)
                    .getDailyUsage(fixture.sender().getAgentId(), AgentDateTimes.today(clock)).notesUsed()).isZero();
        });
    }

    private AgentNoteService service(Runnable beforeOwnerLocks) {
        var ownerLocks = new AgentOwnerIntegrationAdapter(users, mock(SanctionPolicyService.class), em.getEntityManager());
        AgentOwnerPort owners = new AgentOwnerPort() {
            public AgentOwnerSnapshot resolveActiveOwner(Long id) { return ownerLocks.resolveActiveOwner(id); }
            public AgentOwnerSnapshot resolveActiveOwnerForUpdate(Long id) { return ownerLocks.resolveActiveOwnerForUpdate(id); }
            public void lockActiveOwnersForUpdate(Long first, Long second) {
                beforeOwnerLocks.run(); ownerLocks.lockActiveOwnersForUpdate(first, second);
            }
        };
        var policy = mock(AgentPolicyService.class);
        when(policy.resolve(any())).thenReturn(new AgentPolicyService.AgentPolicySnapshot(
                new AgentPolicyService.AgentDailyStatus(LocalDate.now(clock), 0, 0, OffsetDateTime.now(clock).plusDays(1)),
                AgentLimits.builder().notesRemaining(20).build(), AgentRestrictions.builder().canSendNote(true).build(), false));
        var sendPolicy = new AgentNoteSendPolicy(new AgentUserRelationshipIntegrationAdapter(blockService()));
        var audit = mock(AgentAuditService.class);
        var command = new AgentNoteSendCommandService(new AgentQuotaService(dailyQuotas, clock),
                new AgentNoteThreadCommandService(threads), notes, audit, sendPolicy);
        return new AgentNoteService(agents, threads, notes, new AgentOwnershipService(agents, actor -> actor),
                policy, audit, sendPolicy, command, owners, em.getEntityManager(), clock);
    }

    private UserWritableResolver writableUsers() { return new UserWritableResolver(users, mock(SanctionService.class)); }
    private UserBlockService blockService() {
        return new UserBlockService(blocks, mock(UserReadableResolver.class), writableUsers(),
                mock(NotificationAccessInvalidationService.class));
    }
    private TransactionTemplate tx() { return new TransactionTemplate(transactionManager); }
    private AgentNoteSendRequest request(Agent recipient) {
        var request = new AgentNoteSendRequest();
        ReflectionTestUtils.setField(request, "recipientAgentName", recipient.getName());
        ReflectionTestUtils.setField(request, "content", "concurrent note");
        return request;
    }
    private Fixture fixture() { return tx().execute(status -> new Fixture(persistAgent(), persistAgent())); }
    private Agent persistAgent() {
        String name = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        var user = User.builder().loginId(name).password("test-password").email(name + "@example.test").displayName(name).build();
        em.persist(user);
        var agent = Agent.builder().user(user).agentTokenHash(name).name(name).description("test").status(Agent.STATUS_ACTIVE).build();
        em.persistAndFlush(agent);
        return agent;
    }
    private void assertWaiting(int pid) {
        try {
            boolean waiting = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!waiting && System.nanoTime() < deadline) {
                try (var connection = dataSource.getConnection();
                        var statement = connection.prepareStatement("SELECT wait_event_type FROM pg_stat_activity WHERE pid=?")) {
                    statement.setInt(1, pid);
                    try (var rows = statement.executeQuery()) { waiting = rows.next() && "Lock".equals(rows.getString(1)); }
                }
                if (!waiting) Thread.sleep(25);
            }
            assertThat(waiting).as("send must wait on the User rows held by lifecycle/block commands").isTrue();
        } catch (Exception error) { throw new RuntimeException(error); }
    }
    private static <T> T await(CompletableFuture<T> future) {
        try { return future.get(15, TimeUnit.SECONDS); } catch (Exception error) { throw new RuntimeException(error); }
    }
    private static void awaitLatch(CountDownLatch latch) {
        try { assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue(); } catch (InterruptedException error) { throw new RuntimeException(error); }
    }
    private static void stop(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS);
    }
    private record Fixture(Agent sender, Agent receiver) { }
}
