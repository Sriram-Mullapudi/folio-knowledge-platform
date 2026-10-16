package com.ekp.embedding;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/** Pure helpers for vector serialization and retry backoff. No I/O. */
public final class Vectors {

    private Vectors() {
    }

    /**
     * Formats a float vector as a pgvector text literal: {@code [v1,v2,...]}.
     * Passed as a bind parameter and cast with {@code CAST(? AS vector)}, which
     * avoids any dependency on a pgvector-specific JDBC type.
     */
    public static String toLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Exponential backoff with full jitter, capped at {@code max}.
     * Delay for attempt n (1-based) is a random value in
     * {@code [0, min(max, initial * 2^(n-1))]}.
     */
    public static Duration backoff(int attempt, Duration initial, Duration max) {
        long initialMs = initial.toMillis();
        long maxMs = max.toMillis();
        // Cap the shift to avoid overflow on large attempt counts.
        int shift = Math.min(attempt - 1, 30);
        long exp = initialMs << shift;
        long ceiling = Math.min(exp <= 0 ? maxMs : exp, maxMs);
        long jittered = ThreadLocalRandom.current().nextLong(ceiling + 1);
        return Duration.ofMillis(jittered);
    }
}
