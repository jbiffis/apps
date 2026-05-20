package com.biffis.tracker;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared Testcontainers base: one Postgres for the whole suite, Flyway runs
 * the real migrations (so the seeded carley/jeremy rows exist). A fixed test
 * JWT secret keeps tokens reproducible.
 *
 * NOTE: Testcontainers needs a Docker daemon whose API the bundled docker-java
 * client can negotiate. Against very new Docker (29.x) this currently fails;
 * see tracker/docs/EPICS.md "Current work" for the workaround used to verify.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "tracker.jwt.secret=test-secret-at-least-32-chars-long-aaaa",
                "tracker.jwt.ttl-days=1"
        })
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
}
