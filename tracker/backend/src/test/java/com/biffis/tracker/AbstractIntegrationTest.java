package com.biffis.tracker;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared integration-test base. One Postgres for the whole suite; Flyway runs
 * the real migrations so the seeded carley/jeremy rows + catalog exist. Fixed
 * test JWT secret keeps tokens reproducible.
 *
 * Two DB modes:
 *  - **Default (CI / any host with a Testcontainers-compatible Docker):** spins
 *    up postgres:16-alpine via Testcontainers.
 *  - **External (set TRACKER_TEST_DB_URL):** point at an already-running
 *    Postgres instead. Used to run the suite on hosts where the bundled
 *    docker-java can't negotiate with a very new Docker daemon (29.x). See
 *    docs/EPICS.md.
 *
 * The container is started manually (not via @Container) so it's only created
 * in the Testcontainers path; the JVM tears it down on exit.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "tracker.jwt.secret=test-secret-at-least-32-chars-long-aaaa",
                "tracker.jwt.ttl-days=1"
        })
public abstract class AbstractIntegrationTest {

    private static final String EXTERNAL_URL = System.getenv("TRACKER_TEST_DB_URL");
    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        if (EXTERNAL_URL == null) {
            POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
            POSTGRES.start();
        } else {
            POSTGRES = null;
        }
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> System.getenv("TRACKER_TEST_DB_USER"));
            registry.add("spring.datasource.password", () -> System.getenv("TRACKER_TEST_DB_PASSWORD"));
        }
    }
}
