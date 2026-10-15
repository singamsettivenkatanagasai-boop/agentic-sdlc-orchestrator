package com.agentic.orchestrator.model;

import java.util.List;

/** Run-level information handed to agents (the Release and Reviewer agents need it). */
public record RunInfo(String runId, Scenario scenario, List<NodeSummary> nodes, MetricsSnapshot metrics) { }
