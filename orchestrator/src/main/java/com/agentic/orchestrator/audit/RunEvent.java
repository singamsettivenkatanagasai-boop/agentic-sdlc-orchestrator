package com.agentic.orchestrator.audit;

import java.time.Instant;

/**
 * Immutable audit event. Each event carries the hash of its predecessor, so any later edit of
 * the log breaks the chain and is detectable ({@link EventLog#verify}).
 */
public record RunEvent(long seq, Instant ts, String runId, String nodeId, EventType type,
                       String actor, String detail, String prevHash, String hash) { }
