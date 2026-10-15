package com.agentic.orchestrator.engine;

/** Exponential backoff with a cap. The attempt budget itself lives on the node ({@code maxAttempts}). */
public record RetryPolicy(long baseMillis, long maxMillis) {
    public RetryPolicy(long baseMillis) { this(baseMillis, 5_000); }

    /** Delay before attempt {@code nextAttempt} (2 = first retry). */
    public long delayBefore(int nextAttempt) {
        if (nextAttempt <= 1 || baseMillis <= 0) {
            return 0;
        }
        int exp = Math.min(nextAttempt - 2, 20);
        return Math.min(maxMillis, baseMillis * (1L << exp));
    }
}
