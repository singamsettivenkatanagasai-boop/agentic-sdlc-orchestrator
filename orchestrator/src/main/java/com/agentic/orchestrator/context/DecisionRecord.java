package com.agentic.orchestrator.context;

import java.time.Instant;
import java.util.Map;

/**
 * Decision lineage entry: who decided what, why, based on which input artifacts (path -> hash)
 * and which output artifacts (path -> hash). Walking these records answers
 * "which requirement and decisions produced this file?".
 */
public record DecisionRecord(String id, String nodeId, String actor, String decision, String rationale,
                             Map<String, String> inputs, Map<String, String> outputs, Instant at) { }
