package com.weedrice.whiteboard.domain.agent.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/** Only a dedicated disposable database: Hibernate creates/drops this test database's tables. */
@EnabledIfEnvironmentVariable(named = "AGENT_NOTE_TEST_JDBC_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/agent_note_test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AgentNotePostgresTest extends AgentNoteRepositoryTest {
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("AGENT_NOTE_TEST_JDBC_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("AGENT_NOTE_TEST_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("AGENT_NOTE_TEST_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void overlappingObservedReadWaitsForSelectedRowsAndThenReplaysIdempotently() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        var fixture = tx.execute(status -> {
            var owner = persistAgent("pg-owner");
            var sender = persistAgent("pg-sender");
            var thread = persistThread(owner, sender);
            var observed = persistNote(thread, sender, owner, "observed");
            persistNote(thread, sender, owner, "unobserved");
            entityManager.flush();
            return new Object[] {owner, thread.getNoteThreadId(), observed.getNoteId()};
        });
        var owner = (com.weedrice.whiteboard.domain.agent.entity.Agent) fixture[0];
        Long threadId = (Long) fixture[1];
        Long noteId = (Long) fixture[2];
        var pidReady = new CompletableFuture<Integer>();
        var result = new CompletableFuture<com.weedrice.whiteboard.domain.agent.dto.AgentNoteResponses.ReadResponse>();
        var executor = Executors.newSingleThreadExecutor();
        try {
            tx.executeWithoutResult(status -> {
                assertThat(agentNoteRepository.findObservedReceivedNotesForUpdate(threadId, owner.getAgentId(), List.of(noteId)))
                        .hasSize(1);
                executor.submit(() -> {
                    try {
                        result.complete(tx.execute(retry -> {
                            int pid = ((Number) entityManager.getEntityManager().createNativeQuery("SELECT pg_backend_pid()")
                                    .getSingleResult()).intValue();
                            pidReady.complete(pid);
                            return noteService(owner).markObservedRead(owner.getAgentId(), threadId, List.of(noteId), null);
                        }));
                    } catch (Throwable error) { result.completeExceptionally(error); }
                });
                try {
                    int pid = pidReady.get(10, TimeUnit.SECONDS);
                    boolean waiting = false;
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                    while (!waiting && System.nanoTime() < deadline) {
                        try (var connection = dataSource.getConnection();
                                var statement = connection.prepareStatement("SELECT wait_event_type FROM pg_stat_activity WHERE pid=?")) {
                            statement.setInt(1, pid);
                            try (var rows = statement.executeQuery()) {
                                waiting = rows.next() && "Lock".equals(rows.getString(1));
                            }
                        }
                        if (!waiting) Thread.sleep(25);
                    }
                    assertThat(waiting).as("second acknowledgement waits on the selected note row").isTrue();
                    assertThat(result.isDone()).isFalse();
                    assertThat(agentNoteRepository.markObservedReceivedNotesRead(threadId, owner.getAgentId(), List.of(noteId)))
                            .isEqualTo(1);
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            var replay = result.get(15, TimeUnit.SECONDS);
            assertThat(replay.isObservedBoundaryApplied()).isTrue();
            assertThat(replay.getRemainingUnreadCount()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
