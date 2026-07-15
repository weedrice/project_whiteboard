package com.weedrice.whiteboard;

import com.weedrice.whiteboard.domain.auth.service.OAuthSignupTicketService;
import com.weedrice.whiteboard.domain.auth.service.TokenHashService;
import com.weedrice.whiteboard.domain.post.repository.PopularPostAggregationLockRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoadsWithPostgresMigrations() {
    }

    @Test
    void emoticonShopItemMigrationBackfillsAndPreservesCanonicalPrice() throws Exception {
        String schema = "emoticon_migration_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.execute("CREATE SCHEMA " + schema);

        try (Connection connection = dataSource.getConnection()) {
            String originalSchema = connection.getSchema();
            try {
                Flyway flywayToV51 = Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("51"))
                    .load();
                flywayToV51.migrate();

                connection.setSchema(schema);
                JdbcTemplate isolated = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                isolated.update("""
                    INSERT INTO users
                        (user_id, created_at, modified_at, display_name, email, is_email_verified,
                         is_super_admin, login_id, password, status)
                    VALUES
                        (3001, NOW(), NOW(), 'Migration User', 'migration@example.com', 'Y',
                         'N', 'migration-user', 'encoded', 'ACTIVE')
                    """);
                isolated.update("""
                    INSERT INTO emoticon_masters
                        (emoticon_id, created_at, modified_at, is_active, name, purchase_count, tags, thumbnail_url)
                    VALUES
                        (1001, NOW(), NOW(), 'Y', 'Missing Active', 0, ARRAY[]::TEXT[], REPEAT('x', 400)),
                        (1002, NOW(), NOW(), 'N', 'Existing Hidden', 0, ARRAY[]::TEXT[], '/hidden.png')
                    """);
                isolated.update("""
                    INSERT INTO shop_items
                        (item_id, created_at, modified_at, item_name, price, item_type, target_id, image_url, is_active)
                    VALUES
                        (2001, NOW(), NOW(), 'Old Hidden', 250, 'EMOTICON', 1002, '/old.png', 'N'),
                        (2002, NOW(), NOW(), 'Duplicate Hidden', 999, 'EMOTICON', 1002, '/duplicate.png', 'Y'),
                        (2003, NOW(), NOW(), 'Legacy Untargeted', 100, 'EMOTICON', NULL, NULL, 'Y'),
                        (2004, NOW(), NOW(), 'Orphan', 100, 'EMOTICON', 9999, NULL, 'Y')
                    """);
                isolated.update("""
                    INSERT INTO purchase_history
                        (purchase_id, created_at, modified_at, purchased_price, item_id, user_id)
                    VALUES (4001, NOW(), NOW(), 250, 2001, 3001)
                    """);
                isolated.update("""
                    INSERT INTO global_configs
                        (config_key, config_value, description, created_at, modified_at)
                    VALUES ('NOBICON_PRICE', 'invalid', 'legacy invalid price', NOW(), NOW())
                    """);

                Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

                assertEquals(100, isolated.queryForObject(
                    "SELECT price FROM shop_items WHERE item_type = 'EMOTICON' AND target_id = 1001",
                    Integer.class));
                assertEquals("Y", isolated.queryForObject(
                    "SELECT is_active FROM shop_items WHERE item_type = 'EMOTICON' AND target_id = 1001",
                    String.class));
                assertEquals(999, isolated.queryForObject(
                    "SELECT price FROM shop_items WHERE item_type = 'EMOTICON' AND target_id = 1002",
                    Integer.class));
                assertEquals("Existing Hidden", isolated.queryForObject(
                    "SELECT item_name FROM shop_items WHERE item_type = 'EMOTICON' AND target_id = 1002",
                    String.class));
                assertEquals("N", isolated.queryForObject(
                    "SELECT is_active FROM shop_items WHERE item_type = 'EMOTICON' AND target_id = 1002",
                    String.class));
                assertEquals(3, isolated.queryForObject(
                    "SELECT COUNT(*) FROM shop_items WHERE item_type = 'EMOTICON' AND target_id IS NULL",
                    Integer.class));
                assertEquals("N", isolated.queryForObject(
                    "SELECT is_active FROM shop_items WHERE item_id = 2003",
                    String.class));
                assertEquals("N", isolated.queryForObject(
                    "SELECT is_active FROM shop_items WHERE item_id = 2001",
                    String.class));
                assertEquals(0, isolated.queryForObject(
                    "SELECT COUNT(*) FROM shop_items WHERE item_id = 2001 AND target_id IS NOT NULL",
                    Integer.class));
                assertEquals("N", isolated.queryForObject(
                    "SELECT is_active FROM shop_items WHERE item_id = 2004",
                    String.class));
                assertEquals(0, isolated.queryForObject(
                    "SELECT COUNT(*) FROM shop_items WHERE item_id = 2004 AND target_id IS NOT NULL",
                    Integer.class));
                assertEquals(2001L, isolated.queryForObject(
                    "SELECT item_id FROM purchase_history WHERE purchase_id = 4001",
                    Long.class));
                assertEquals("100", isolated.queryForObject(
                    "SELECT config_value FROM global_configs WHERE config_key = 'NOBICON_PRICE'",
                    String.class));

                assertThrows(DuplicateKeyException.class, () -> isolated.update("""
                    INSERT INTO shop_items
                        (created_at, modified_at, item_name, price, item_type, target_id, is_active)
                    VALUES (NOW(), NOW(), 'Duplicate Target', 100, 'EMOTICON', 1001, 'N')
                    """));
            } finally {
                connection.setSchema(originalSchema);
            }
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
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
