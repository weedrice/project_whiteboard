package com.weedrice.whiteboard;

import com.weedrice.whiteboard.domain.auth.service.OAuthSignupTicketService;
import com.weedrice.whiteboard.domain.auth.service.TokenHashService;
import com.weedrice.whiteboard.domain.auth.service.VerificationCodeService;
import com.weedrice.whiteboard.domain.auth.service.LoginClientMetadata;
import com.weedrice.whiteboard.domain.auth.service.SessionTokenService;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.post.repository.PopularPostAggregationLockRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardVisitRepository;
import com.weedrice.whiteboard.domain.notification.service.KeywordSubscriptionService;
import com.weedrice.whiteboard.domain.notification.dto.PushSubscriptionRequest;
import com.weedrice.whiteboard.domain.notification.service.PushSubscriptionService;
import com.weedrice.whiteboard.domain.notification.repository.NotificationDeliveryJobRepository;
import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.SecurityAuthorities;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;

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
    private VerificationCodeService verificationCodeService;

    @Autowired
    private SessionTokenService sessionTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BoardVisitRepository boardVisitRepository;

    @Autowired
    private KeywordSubscriptionService keywordSubscriptionService;

    @Autowired
    private PushSubscriptionService pushSubscriptionService;

    @Autowired
    private NotificationDeliveryJobRepository notificationDeliveryJobRepository;

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
    void verificationIndexRefreshFamilyAndDomainLockMigrationsAreApplied() {
        Integer failedAttemptColumns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'verification_codes'
                  AND column_name = 'failed_attempts'
                  AND is_nullable = 'NO'
                  AND column_default LIKE '0%'
                """, Integer.class);
        Integer attemptConstraints = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_constraint
                WHERE connamespace = current_schema()::regnamespace
                  AND conname = 'chk_verification_codes_failed_attempts'
                """, Integer.class);
        Integer redundantIndexes = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND indexname IN (
                    'idx_agent_daily_quotas_lookup',
                    'idx_refresh_tokens_hash',
                    'idx_agent_note_threads_low',
                    'idx_user_keyword_subscriptions_user',
                    'idx_comment_mentions_comment',
                    'idx_poll_options_poll',
                    'idx_poll_votes_poll',
                    'idx_user_attendance_user_date'
                  )
                """, Integer.class);
        Integer uniqueConstraints = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_constraint
                WHERE connamespace = current_schema()::regnamespace
                  AND conname IN (
                    'uk_agent_daily_quotas_agent_date_action',
                    'refresh_tokens_token_hash_key',
                    'uk_agent_note_threads_pair',
                    'uk_user_keyword_subscriptions_user_keyword',
                    'uk_comment_mentions_comment_user',
                    'uk_poll_options_poll_sort',
                    'uk_poll_votes_user_option',
                    'uk_user_attendance_user_date'
                  )
                """, Integer.class);
        Integer sessionFamilyColumns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'refresh_tokens'
                  AND column_name = 'session_family_id'
                  AND data_type = 'uuid'
                  AND is_nullable = 'NO'
                """, Integer.class);
        Integer sessionFamilyIndexes = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND indexname = 'idx_refresh_tokens_family_active'
                  AND indexdef LIKE '%(session_family_id, is_revoked)%'
                """, Integer.class);
        Integer boardOrderLocks = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM domain_locks
                WHERE lock_name = 'BOARD_ORDER'
                """, Integer.class);

        assertEquals(1, failedAttemptColumns);
        assertEquals(1, attemptConstraints);
        assertEquals(0, redundantIndexes);
        assertEquals(8, uniqueConstraints);
        assertEquals(1, sessionFamilyColumns);
        assertEquals(1, sessionFamilyIndexes);
        assertEquals(1, boardOrderLocks);
    }

    @Test
    void refreshTokenSessionFamilyMigrationBackfillsExistingRows() throws Exception {
        String schema = "refresh_family_migration_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.execute("CREATE SCHEMA " + schema);

        try (Connection connection = dataSource.getConnection()) {
            String originalSchema = connection.getSchema();
            try {
                Flyway.configure()
                        .dataSource(dataSource)
                        .schemas(schema)
                        .defaultSchema(schema)
                        .locations("classpath:db/migration")
                        .target(MigrationVersion.fromVersion("56"))
                        .load()
                        .migrate();

                connection.setSchema(schema);
                JdbcTemplate isolated = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                isolated.update("""
                        INSERT INTO users
                            (user_id, created_at, modified_at, display_name, email, is_email_verified,
                             is_super_admin, login_id, password, status)
                        VALUES
                            (5701, NOW(), NOW(), 'Refresh Family User', 'refresh-family@example.com', 'Y',
                             'N', 'refresh-family-user', 'encoded', 'ACTIVE')
                        """);
                isolated.update("""
                        INSERT INTO refresh_tokens
                            (created_at, modified_at, expires_at, ip_address, is_revoked, token_hash, user_id)
                        VALUES
                            (NOW(), NOW(), NOW() + INTERVAL '7 days', '127.0.0.1', 'N', 'legacy-token-hash', 5701)
                        """);

                Flyway.configure()
                        .dataSource(dataSource)
                        .schemas(schema)
                        .defaultSchema(schema)
                        .locations("classpath:db/migration")
                        .load()
                        .migrate();

                assertTrue(isolated.queryForObject(
                        "SELECT session_family_id IS NOT NULL FROM refresh_tokens WHERE token_hash = ?",
                        Boolean.class,
                        "legacy-token-hash"));
                assertEquals(1, isolated.queryForObject("""
                        SELECT COUNT(*)
                        FROM pg_indexes
                        WHERE schemaname = ?
                          AND indexname = 'idx_refresh_tokens_family_active'
                        """, Integer.class, schema));
            } finally {
                connection.setSchema(originalSchema);
            }
        } finally {
            jdbcTemplate.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void concurrentWrongVerificationCodesCommitAndExhaustTheCode() throws Exception {
        String email = "postgres-verification-attempts-" + UUID.randomUUID() + "@example.com";
        String rawCode = "123456";
        jdbcTemplate.update("""
                INSERT INTO verification_codes
                    (created_at, modified_at, code, delivery_status, email, expiry_date,
                     is_ticket_consumed, is_verified, purpose, failed_attempts)
                VALUES
                    (NOW(), NOW(), ?, 'SENT', ?, NOW() + INTERVAL '10 minutes',
                     'N', 'N', 'SIGNUP', 0)
                """, tokenHashService.hashSha256(rawCode), email);

        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> attempts = java.util.stream.IntStream.range(0, 5)
                    .mapToObj(ignored -> executor.submit(() -> {
                        start.await(5, TimeUnit.SECONDS);
                        try {
                            verificationCodeService.verifyCode(email, "000000", VerificationPurpose.SIGNUP);
                            return false;
                        } catch (BusinessException expected) {
                            return true;
                        }
                    }))
                    .toList();
            start.countDown();
            for (Future<Boolean> attempt : attempts) {
                assertTrue(attempt.get(10, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(5, jdbcTemplate.queryForObject(
                "SELECT failed_attempts FROM verification_codes WHERE email = ?",
                Integer.class,
                email));
        assertThrows(BusinessException.class, () ->
                verificationCodeService.verifyCode(email, rawCode, VerificationPurpose.SIGNUP));
    }

    @Test
    void concurrentRefreshAndLogoutLeaveSessionFamilyRevokedAndOtherFamilyActive() throws Exception {
        String unique = UUID.randomUUID().toString();
        User user = userRepository.saveAndFlush(User.builder()
                .loginId("refresh-race-" + unique)
                .email("refresh-race-" + unique + "@example.com")
                .password("encoded")
                .displayName("Refresh Race User")
                .build());
        var authorities = SecurityAuthorities.user(false);
        var authentication = new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(
                        user.getUserId(), user.getLoginId(), "", true, true, true, true, authorities),
                "",
                authorities);
        String racedToken = sessionTokenService.issueTokens(
                authentication,
                user,
                new LoginClientMetadata("127.0.0.1", "race-browser")).getRefreshToken();
        String retainedToken = sessionTokenService.issueTokens(
                authentication,
                user,
                new LoginClientMetadata("127.0.0.2", "retained-browser")).getRefreshToken();
        UUID racedFamily = jdbcTemplate.queryForObject(
                "SELECT session_family_id FROM refresh_tokens WHERE token_hash = ?",
                UUID.class,
                tokenHashService.hashSha256(racedToken));
        UUID retainedFamily = jdbcTemplate.queryForObject(
                "SELECT session_family_id FROM refresh_tokens WHERE token_hash = ?",
                UUID.class,
                tokenHashService.hashSha256(retainedToken));
        assertNotEquals(racedFamily, retainedFamily);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> refresh = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                try {
                    sessionTokenService.refresh(racedToken);
                } catch (BusinessException expectedRaceOutcome) {
                    assertTrue(expectedRaceOutcome.getErrorCode()
                            == com.weedrice.whiteboard.global.exception.ErrorCode.INVALID_REFRESH_TOKEN);
                }
                return null;
            });
            Future<?> logout = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                sessionTokenService.logout(racedToken);
                return null;
            });
            start.countDown();
            refresh.get(15, TimeUnit.SECONDS);
            logout.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM refresh_tokens
                WHERE session_family_id = ?
                  AND is_revoked = 'N'
                """, Integer.class, racedFamily));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM refresh_tokens
                WHERE session_family_id = ?
                  AND is_revoked = 'N'
                """, Integer.class, retainedFamily));
    }

    @Test
    void concurrentFirstBoardVisitsUpsertOneRow() throws Exception {
        String unique = UUID.randomUUID().toString();
        User user = userRepository.saveAndFlush(User.builder()
                .loginId("visit-race-" + unique)
                .email("visit-race-" + unique + "@example.com")
                .password("encoded")
                .displayName("Visit Race User")
                .build());
        Long boardId = jdbcTemplate.queryForObject("""
                INSERT INTO boards
                    (created_at, modified_at, board_name, board_url, description, creator_id,
                     sort_order, is_active, allow_nsfw, is_public, agent_use_yn)
                VALUES (NOW(), NOW(), ?, ?, '', ?, 1, 'Y', 'N', 'Y', 'N')
                RETURNING board_id
                """, Long.class, "Visit " + unique, "visit-" + unique, user.getUserId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return boardVisitRepository.upsert(user.getUserId(), "visit-" + unique, LocalDateTime.now());
            });
            Future<Integer> second = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return boardVisitRepository.upsert(user.getUserId(), "visit-" + unique, LocalDateTime.now());
            });
            start.countDown();
            assertEquals(1, first.get(10, TimeUnit.SECONDS));
            assertEquals(1, second.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM board_visits WHERE user_id = ? AND board_id = ?",
                Integer.class, user.getUserId(), boardId));
    }

    @Test
    void concurrentKeywordSubscriptionsKeepTenItemLimit() throws Exception {
        String unique = UUID.randomUUID().toString();
        User user = userRepository.saveAndFlush(User.builder()
                .loginId("keyword-race-" + unique)
                .email("keyword-race-" + unique + "@example.com")
                .password("encoded")
                .displayName("Keyword Race User")
                .build());
        for (int index = 0; index < 9; index++) {
            jdbcTemplate.update("""
                    INSERT INTO user_keyword_subscriptions
                        (created_at, modified_at, user_id, keyword)
                    VALUES (NOW(), NOW(), ?, ?)
                    """, user.getUserId(), "existing-" + index);
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = List.of("candidate-a", "candidate-b").stream()
                    .map(keyword -> executor.submit(() -> {
                        start.await(5, TimeUnit.SECONDS);
                        try {
                            keywordSubscriptionService.subscribe(user.getUserId(), keyword);
                            return true;
                        } catch (BusinessException expectedLimit) {
                            return false;
                        }
                    }))
                    .toList();
            start.countDown();
            int successes = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertEquals(1, successes);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(10, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_keyword_subscriptions WHERE user_id = ?",
                Integer.class, user.getUserId()));
    }

    @Test
    void concurrentDuplicateKeywordSubscriptionsAreIdempotent() throws Exception {
        String unique = UUID.randomUUID().toString();
        User user = userRepository.saveAndFlush(User.builder()
                .loginId("keyword-duplicate-" + unique)
                .email("keyword-duplicate-" + unique + "@example.com")
                .password("encoded")
                .displayName("Keyword Duplicate User")
                .build());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Void>> results = java.util.stream.IntStream.range(0, 2)
                    .mapToObj(ignored -> executor.submit(() -> {
                        start.await(5, TimeUnit.SECONDS);
                        keywordSubscriptionService.subscribe(user.getUserId(), "same-keyword");
                        return (Void) null;
                    }))
                    .toList();
            start.countDown();
            for (Future<Void> result : results) {
                result.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_keyword_subscriptions WHERE user_id = ? AND keyword = ?",
                Integer.class, user.getUserId(), "same-keyword"));
    }

    @Test
    void concurrentSameEndpointSubscriptionsKeepOneOwnerAndConsistentSettings() throws Exception {
        String unique = UUID.randomUUID().toString();
        User firstUser = userRepository.saveAndFlush(User.builder()
                .loginId("push-race-a-" + unique)
                .email("push-race-a-" + unique + "@example.com")
                .password("encoded")
                .displayName("Push Race A")
                .build());
        User secondUser = userRepository.saveAndFlush(User.builder()
                .loginId("push-race-b-" + unique)
                .email("push-race-b-" + unique + "@example.com")
                .password("encoded")
                .displayName("Push Race B")
                .build());
        String endpoint = "https://push.example.test/" + unique;

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Void>> subscriptions = List.of(firstUser, secondUser).stream()
                    .map(user -> executor.submit(() -> {
                        start.await(5, TimeUnit.SECONDS);
                        pushSubscriptionService.subscribe(
                                user.getUserId(),
                                pushSubscriptionRequest(endpoint, "key-" + user.getUserId()));
                        return (Void) null;
                    }))
                    .toList();
            start.countDown();
            for (Future<Void> subscription : subscriptions) {
                subscription.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM push_subscriptions WHERE endpoint = ?",
                Integer.class,
                endpoint));
        Long ownerId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM push_subscriptions WHERE endpoint = ?",
                Long.class,
                endpoint);
        Long otherId = ownerId.equals(firstUser.getUserId())
                ? secondUser.getUserId()
                : firstUser.getUserId();
        assertEquals("Y", jdbcTemplate.queryForObject(
                "SELECT push_enabled FROM user_settings WHERE user_id = ?",
                String.class,
                ownerId));
        assertEquals("N", jdbcTemplate.queryForObject(
                "SELECT push_enabled FROM user_settings WHERE user_id = ?",
                String.class,
                otherId));
    }

    private PushSubscriptionRequest pushSubscriptionRequest(String endpoint, String keySuffix) {
        PushSubscriptionRequest.Keys keys = new PushSubscriptionRequest.Keys();
        keys.setP256dh("p256dh-" + keySuffix);
        keys.setAuth("auth-" + keySuffix);
        PushSubscriptionRequest request = new PushSubscriptionRequest();
        request.setEndpoint(endpoint);
        request.setKeys(keys);
        request.setUserAgent("postgres-smoke");
        return request;
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
    void globalConfigContractMigrationSeedsMissingKeysAndPreservesOperatorValue() throws Exception {
        String schema = "global_config_migration_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.execute("CREATE SCHEMA " + schema);

        try (Connection connection = dataSource.getConnection()) {
            String originalSchema = connection.getSchema();
            try {
                Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("52"))
                    .load()
                    .migrate();

                connection.setSchema(schema);
                JdbcTemplate isolated = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                LocalDateTime operatorModifiedAt = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
                isolated.update("""
                    INSERT INTO global_configs
                        (config_key, config_value, description, created_at, modified_at)
                    VALUES ('POINT_SIGNUP_BONUS', '777', 'operator override', ?, ?)
                    """, operatorModifiedAt, operatorModifiedAt);

                Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

                assertEquals("777", isolated.queryForObject(
                    "SELECT config_value FROM global_configs WHERE config_key = 'POINT_SIGNUP_BONUS'",
                    String.class));
                assertEquals("operator override", isolated.queryForObject(
                    "SELECT description FROM global_configs WHERE config_key = 'POINT_SIGNUP_BONUS'",
                    String.class));
                assertEquals(operatorModifiedAt, isolated.queryForObject(
                    "SELECT modified_at FROM global_configs WHERE config_key = 'POINT_SIGNUP_BONUS'",
                    LocalDateTime.class));
                assertEquals("500", isolated.queryForObject(
                    "SELECT config_value FROM global_configs WHERE config_key = 'POINT_BOARD_CREATE_COST'",
                    String.class));
                assertEquals("50", isolated.queryForObject(
                    "SELECT config_value FROM global_configs WHERE config_key = 'POINT_POST_CREATE_REWARD'",
                    String.class));
                assertEquals("10", isolated.queryForObject(
                    "SELECT config_value FROM global_configs WHERE config_key = 'POINT_COMMENT_CREATE_REWARD'",
                    String.class));
                assertEquals("신규 노비콘 상품 기본 가격 (기존 상품 가격에는 적용되지 않음)",
                    isolated.queryForObject(
                        "SELECT description FROM global_configs WHERE config_key = 'NOBICON_PRICE'",
                        String.class));
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

    @Test
    void passwordResetUserVerificationTokenLockOrderSerializesConcurrentWorkers() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.saveAndFlush(User.builder()
                .loginId("password-lock-" + suffix)
                .email("password-lock-" + suffix + "@example.com")
                .password("password")
                .displayName("Password Lock")
                .build());
        LocalDateTime now = LocalDateTime.now();
        Long verificationId = jdbcTemplate.queryForObject("""
                INSERT INTO verification_codes (
                    created_at, modified_at, code, delivery_status, email, expiry_date,
                    is_ticket_consumed, is_verified, purpose
                ) VALUES (?, ?, ?, 'SENT', ?, ?, 'N', 'Y', 'PASSWORD_RESET')
                RETURNING verification_id
                """, Long.class, now, now, "hash", user.getEmail(), now.plusHours(1));
        Long tokenId = jdbcTemplate.queryForObject("""
                INSERT INTO password_reset_tokens (
                    created_at, modified_at, delivery_status, expiry_date, is_used,
                    token, user_id, verification_id
                ) VALUES (?, ?, 'SENT', ?, 'N', ?, ?, ?)
                RETURNING token_id
                """, Long.class, now, now, now.plusHours(1), tokenHashService.hashSha256(suffix),
                user.getUserId(), verificationId);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> workers = List.of(
                    executor.submit(() -> lockPasswordResetState(start, user.getUserId(), verificationId, tokenId)),
                    executor.submit(() -> lockPasswordResetState(start, user.getUserId(), verificationId, tokenId)));
            start.countDown();
            assertTrue(workers.get(0).get(10, TimeUnit.SECONDS));
            assertTrue(workers.get(1).get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void notificationDeliveryInsertIsIdempotentUnderPostgresConcurrency() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User receiver = userRepository.saveAndFlush(User.builder()
                .loginId("delivery-idempotent-" + suffix)
                .email("delivery-idempotent-" + suffix + "@example.com")
                .password("password")
                .displayName("Delivery Idempotent")
                .build());
        NotificationEvent event = new NotificationEvent(
                receiver, null, NotificationType.SYSTEM, NotificationSourceType.SYSTEM, 1L, "delivery");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> inserts = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(ignored -> executor.submit(() -> {
                        start.await(5, TimeUnit.SECONDS);
                        LocalDateTime now = LocalDateTime.now();
                        return notificationDeliveryJobRepository.insertIfAbsent(
                                NotificationDeliveryJob.from(event, now), now);
                    }))
                    .toList();
            start.countDown();
            int inserted = 0;
            for (Future<Integer> insert : inserts) {
                inserted += insert.get(10, TimeUnit.SECONDS);
            }
            assertEquals(1, inserted);
            assertTrue(notificationDeliveryJobRepository.findByEventId(event.getEventId()).isPresent());
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean lockPasswordResetState(
            CountDownLatch start, Long userId, Long verificationId, Long tokenId) throws InterruptedException {
        start.await(5, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(new TransactionTemplate(transactionManager).execute(status -> {
            jdbcTemplate.queryForObject("SELECT user_id FROM users WHERE user_id = ? FOR UPDATE", Long.class, userId);
            jdbcTemplate.queryForObject("SELECT verification_id FROM verification_codes WHERE verification_id = ? FOR UPDATE",
                    Long.class, verificationId);
            jdbcTemplate.queryForObject("SELECT token_id FROM password_reset_tokens WHERE token_id = ? FOR UPDATE",
                    Long.class, tokenId);
            return true;
        }));
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
