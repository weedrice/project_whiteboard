package com.weedrice.whiteboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("postgres-smoke")
@EnabledIfEnvironmentVariable(named = "POSTGRES_SMOKE_TEST", matches = "true")
class PostgresApplicationContextSmokeTest {

    @Test
    void contextLoadsWithPostgresMigrations() {
    }
}
