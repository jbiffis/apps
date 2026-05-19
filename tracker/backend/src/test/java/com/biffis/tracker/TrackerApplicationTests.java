package com.biffis.tracker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke test: spins up a real Postgres via Testcontainers, lets Spring Boot
 * autoconfigure against it, and verifies Flyway runs all migrations cleanly
 * and the context loads.
 *
 * Requires Docker on the machine running tests.
 */
@SpringBootTest(properties = {
    "tracker.jwt.secret=test-secret-at-least-32-chars-long-aaaa"
})
@Testcontainers
class TrackerApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoads() {
        // Pass = Flyway applied all migrations + entities (none yet) validate + beans wire up.
    }
}
