package com.weedrice.whiteboard;

import com.weedrice.whiteboard.domain.auth.service.OAuthSignupTicketService;
import com.weedrice.whiteboard.domain.auth.service.TokenHashService;
import com.weedrice.whiteboard.domain.post.repository.PopularPostAggregationLockRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("postgres-smoke")
@EnabledIfEnvironmentVariable(named = "POSTGRES_SMOKE_TEST", matches = "true")
class PostgresApplicationContextSmokeTest {

    @Autowired
    private PopularPostAggregationLockRepository aggregationLockRepository;

    @Autowired
    private OAuthSignupTicketService ticketService;

    @Autowired
    private TokenHashService tokenHashService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void contextLoadsWithPostgresMigrations() {
    }

    @Test
    void advisoryTransactionLockAllowsOnlyOneConcurrentOwner() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstHasLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        try {
            Future<Boolean> first = executor.submit(() -> transactions.execute(status -> {
                boolean acquired = aggregationLockRepository.tryAcquireTransactionLock();
                firstHasLock.countDown();
                await(releaseFirst);
                return acquired;
            }));

            assertTrue(firstHasLock.await(5, TimeUnit.SECONDS));
            Future<Boolean> second = executor.submit(() ->
                    transactions.execute(status -> aggregationLockRepository.tryAcquireTransactionLock()));

            assertFalse(second.get(5, TimeUnit.SECONDS));
            releaseFirst.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void oauthTicketStoresOnlyHashAndRollbackRestoresConsumption() {
        String email = "postgres-ticket-rollback@example.com";
        String ticket = ticketService.issue(email, null, "google", "provider-rollback");

        String storedHash = jdbcTemplate.queryForObject(
                "select ticket_hash from oauth_signup_tickets where email = ?",
                String.class,
                email);
        assertNotEquals(ticket, storedHash);
        assertEquals(tokenHashService.hashSha256(ticket), storedHash);

        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        transactions.executeWithoutResult(status -> {
            ticketService.consume(ticket);
            status.setRollbackOnly();
        });

        assertEquals(email, ticketService.consume(ticket).email());
    }

    @Test
    void oauthTicketCanBeConsumedOnlyOnceConcurrently() throws Exception {
        String ticket = ticketService.issue(
                "postgres-ticket-concurrent@example.com",
                "Concurrent",
                "google",
                "provider-concurrent");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Boolean>> attempts = List.of(
                    executor.submit(() -> consumeAfter(start, ticket)),
                    executor.submit(() -> consumeAfter(start, ticket)));
            start.countDown();

            long successes = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(10, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertEquals(1, successes);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean consumeAfter(CountDownLatch start, String ticket) throws InterruptedException {
        start.await(5, TimeUnit.SECONDS);
        try {
            ticketService.consume(ticket);
            return true;
        } catch (BusinessException expected) {
            return false;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while holding PostgreSQL advisory lock");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding PostgreSQL advisory lock", exception);
        }
    }
}
