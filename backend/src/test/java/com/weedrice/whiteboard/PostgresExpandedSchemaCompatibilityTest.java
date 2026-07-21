package com.weedrice.whiteboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "web-push.allowed-hosts=push.example.test")
@ActiveProfiles("postgres-smoke")
@EnabledIfEnvironmentVariable(named = "POSTGRES_EXPANDED_SCHEMA_TEST", matches = "true")
class PostgresExpandedSchemaCompatibilityTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsAgainstExpandedSchemaWithoutRunningFlyway() {
        assertEquals(1, jdbcTemplate.queryForObject("SELECT 1", Integer.class));
    }
}
