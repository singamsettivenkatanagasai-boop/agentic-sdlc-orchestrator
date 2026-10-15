package com.agentic.orchestrator.model;

/**
 * Reliability metrics of one run.
 *
 * @param firstAttemptSuccessRate share of completed nodes that never failed or needed a retry
 * @param avgMttrMillis           mean time from a node's first failure to its eventual success
 * @param endToEndMillis          wall-clock run latency (so far, if the run is still active)
 */
public record MetricsSnapshot(int totalNodes, int doneNodes, double firstAttemptSuccessRate,
                              int retries, int rollbacks, int fallbacks, int safeStops,
                              int replans, int approvals, double avgMttrMillis,
                              long endToEndMillis, RunStatus status) { }
