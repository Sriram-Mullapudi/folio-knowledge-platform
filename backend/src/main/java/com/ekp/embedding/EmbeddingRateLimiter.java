package com.ekp.embedding;

/**
 * Minimal thread-safe rate limiter that spaces requests to stay under a
 * requests-per-minute cap. Each {@link #acquire()} blocks until at least
 * {@code 60000 / rpm} ms have elapsed since the previously granted permit.
 *
 * <p>This is intentionally simple (a single shared gate) rather than a full
 * token bucket: embedding traffic is low-cardinality and bursty, and spacing
 * is enough to avoid provider 429s. Provider-side limits remain the source of
 * truth — the retry logic handles the occasional 429 that slips through.
 */
public class EmbeddingRateLimiter {

    private final long minIntervalMs;
    private long nextAllowedAt;

    public EmbeddingRateLimiter(int requestsPerMinute) {
        this.minIntervalMs = requestsPerMinute <= 0 ? 0 : 60_000L / requestsPerMinute;
    }

    public void acquire() throws InterruptedException {
        if (minIntervalMs == 0) {
            return;
        }
        long waitMs;
        synchronized (this) {
            long now = System.currentTimeMillis();
            long start = Math.max(now, nextAllowedAt);
            nextAllowedAt = start + minIntervalMs;
            waitMs = start - now;
        }
        if (waitMs > 0) {
            Thread.sleep(waitMs);
        }
    }
}
