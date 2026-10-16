package com.ekp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: verifies the Spring context starts and all beans wire together,
 * including Flyway migrations against a real pgvector-enabled PostgreSQL started
 * by {@link TestcontainersConfiguration}. If the context fails to load, this
 * fails - cheap insurance that the wiring is sound.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class EkpApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty: failure to load the context fails the test.
    }
}
