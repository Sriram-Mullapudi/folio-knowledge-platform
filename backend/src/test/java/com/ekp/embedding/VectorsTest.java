package com.ekp.embedding;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class VectorsTest {

    @Test
    void formatsPgvectorLiteral() {
        assertThat(Vectors.toLiteral(new float[] {1.0f, 2.5f, -3.0f}))
                .isEqualTo("[1.0,2.5,-3.0]");
    }

    @Test
    void emptyVectorLiteral() {
        assertThat(Vectors.toLiteral(new float[] {})).isEqualTo("[]");
    }

    @Test
    void backoffStaysWithinBounds() {
        Duration initial = Duration.ofMillis(100);
        Duration max = Duration.ofSeconds(2);
        for (int attempt = 1; attempt <= 12; attempt++) {
            for (int trial = 0; trial < 50; trial++) {
                long ms = Vectors.backoff(attempt, initial, max).toMillis();
                assertThat(ms).isBetween(0L, max.toMillis());
            }
        }
    }

    @Test
    void firstAttemptCappedByInitial() {
        Duration initial = Duration.ofMillis(100);
        Duration max = Duration.ofSeconds(2);
        for (int trial = 0; trial < 100; trial++) {
            // attempt 1 ceiling is `initial`, so it can never exceed it.
            assertThat(Vectors.backoff(1, initial, max).toMillis())
                    .isLessThanOrEqualTo(initial.toMillis());
        }
    }
}
