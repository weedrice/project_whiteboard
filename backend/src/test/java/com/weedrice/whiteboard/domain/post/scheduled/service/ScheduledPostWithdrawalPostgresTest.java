package com.weedrice.whiteboard.domain.post.scheduled.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

/** Runs the same withdrawal and stale-lease assertions against migrated PostgreSQL. */
@DataJpaTest(showSql = false, properties = "spring.flyway.postgresql.transactional-lock=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("postgres-smoke")
@EnabledIfEnvironmentVariable(named = "POSTGRES_SMOKE_TEST", matches = "true")
class ScheduledPostWithdrawalPostgresTest extends ScheduledPostWithdrawalRepositoryTest {
}
