package com.biffis.tracker;

import org.junit.jupiter.api.Test;

/**
 * Smoke test: real Postgres via Testcontainers (see {@link AbstractIntegrationTest}),
 * Spring autoconfigures against it, Flyway runs all migrations, context loads.
 */
class TrackerApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // Pass = Flyway applied all migrations + entities validate + beans wire up.
    }
}
