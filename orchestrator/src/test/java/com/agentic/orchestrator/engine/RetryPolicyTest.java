package com.agentic.orchestrator.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetryPolicyTest {

    @Test
    void firstAttemptHasNoDelayAndRetriesBackOffExponentially() {
        RetryPolicy p = new RetryPolicy(200);
        assertEquals(0, p.delayBefore(1));
        assertEquals(200, p.delayBefore(2));
        assertEquals(400, p.delayBefore(3));
        assertEquals(800, p.delayBefore(4));
    }

    @Test
    void delayIsCapped() {
        assertEquals(5_000, new RetryPolicy(200).delayBefore(30));
        assertEquals(1_000, new RetryPolicy(200, 1_000).delayBefore(10));
    }

    @Test
    void zeroBaseDisablesBackoff() {
        assertEquals(0, new RetryPolicy(0).delayBefore(5));
    }
}
