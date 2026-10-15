package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.graph.NodeState;
import com.agentic.orchestrator.graph.TaskNode;
import com.agentic.orchestrator.graph.WorkflowGraph;
import com.agentic.orchestrator.model.MetricsSnapshot;
import com.agentic.orchestrator.model.RunStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collects the reliability metrics of one run: first-attempt success rate, retries, rollbacks,
 * fallbacks, safe-stops, re-plans, approvals, MTTR and end-to-end latency. Every increment is
 * also forwarded to the {@link MetricsSink} so the numbers appear in Actuator/Micrometer.
 */
public final class MetricsCollector {
    private final MetricsSink sink;
    private final AtomicInteger retries = new AtomicInteger();
    private final AtomicInteger rollbacks = new AtomicInteger();
    private final AtomicInteger fallbacks = new AtomicInteger();
    private final AtomicInteger safeStops = new AtomicInteger();
    private final AtomicInteger replans = new AtomicInteger();
    private final AtomicInteger approvals = new AtomicInteger();
    private final AtomicInteger acceptances = new AtomicInteger();
    private final AtomicInteger firstAttemptSuccesses = new AtomicInteger();
    private final List<Long> mttrSamples = new CopyOnWriteArrayList<>();

    public MetricsCollector(MetricsSink sink) { this.sink = sink == null ? MetricsSink.NOOP : sink; }

    public void retry() { retries.incrementAndGet(); sink.count("agentic.retries"); }
    public void rollback() { rollbacks.incrementAndGet(); sink.count("agentic.rollbacks"); }
    public void fallback() { fallbacks.incrementAndGet(); sink.count("agentic.fallbacks"); }
    public void safeStop() { safeStops.incrementAndGet(); sink.count("agentic.safe_stops"); }
    public void replan() { replans.incrementAndGet(); sink.count("agentic.replans"); }
    public void approval() { approvals.incrementAndGet(); sink.count("agentic.approvals"); }

    public int replans() { return replans.get(); }

    /**
     * Called once per accepted node.
     *
     * @param failedAttempts attempts that failed before this one succeeded
     * @param mttrMillis     time from first failure to success, or -1 if the node never failed
     */
    public void accepted(int failedAttempts, long mttrMillis) {
        acceptances.incrementAndGet();
        if (failedAttempts == 0) {
            firstAttemptSuccesses.incrementAndGet();
        }
        if (mttrMillis >= 0) {
            mttrSamples.add(mttrMillis);
            sink.timer("agentic.mttr", mttrMillis);
        }
    }

    public MetricsSnapshot snapshot(WorkflowGraph graph, RunStatus status, Instant start, Instant end) {
        List<TaskNode> nodes = graph.nodes();
        int done = (int) nodes.stream().filter(n -> n.state() == NodeState.DONE).count();
        int acc = acceptances.get();
        double rate = acc == 0 ? 1.0 : (double) firstAttemptSuccesses.get() / acc;
        double mttr = mttrSamples.isEmpty() ? 0
                : mttrSamples.stream().mapToLong(Long::longValue).average().orElse(0);
        Instant until = end == null ? Instant.now() : end;
        long latency = start == null ? 0 : Duration.between(start, until).toMillis();
        return new MetricsSnapshot(nodes.size(), done, rate, retries.get(), rollbacks.get(), fallbacks.get(),
                safeStops.get(), replans.get(), approvals.get(), mttr, latency, status);
    }
}
