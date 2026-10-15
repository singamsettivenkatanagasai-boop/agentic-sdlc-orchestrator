package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.audit.EventLog;
import com.agentic.orchestrator.context.RunContext;
import com.agentic.orchestrator.graph.WorkflowGraph;
import com.agentic.orchestrator.model.MetricsSnapshot;
import com.agentic.orchestrator.model.RunInfo;
import com.agentic.orchestrator.model.RunOptions;
import com.agentic.orchestrator.model.RunOptions.ScheduledChange;
import com.agentic.orchestrator.model.RunStatus;
import com.agentic.orchestrator.model.Scenario;
import com.agentic.orchestrator.workspace.Workspace;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * All state of one orchestration run: the graph, the shared context, the audit log, the
 * workspace and the metrics. Created by the {@code RunManager}, driven by the {@link RunEngine}.
 */
public final class Run {
    private final String id;
    private final String label;
    private final Scenario scenario;
    private final RunOptions options;
    private final RunContext context;
    private final WorkflowGraph graph;
    private final EventLog log;
    private final Workspace workspace;
    private final MetricsCollector metrics;

    private final List<ScheduledChange> scheduledChanges = new CopyOnWriteArrayList<>();
    private final Map<String, Set<String>> outputsByNode = new ConcurrentHashMap<>();
    private final Set<String> invalidated = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private final AtomicReference<String> stopReason = new AtomicReference<>();

    private final CountDownLatch closed = new CountDownLatch(1);

    private volatile RunStatus status = RunStatus.CREATED;
    private volatile Instant startedAt;
    private volatile Instant finishedAt;

    public Run(String id, String label, Scenario scenario, String requirement, RunOptions options,
               Workspace workspace, EventLog log, WorkflowGraph graph, MetricsSink sink) {
        this.id = id;
        this.label = label;
        this.scenario = scenario;
        this.options = options;
        this.workspace = workspace;
        this.log = log;
        this.graph = graph;
        this.context = new RunContext(id, scenario, requirement);
        this.metrics = new MetricsCollector(sink);
        if (options.change() != null) {
            scheduledChanges.add(options.change());
        }
    }

    public String id() { return id; }
    public String label() { return label; }
    public Scenario scenario() { return scenario; }
    public RunOptions options() { return options; }
    public RunContext context() { return context; }
    public WorkflowGraph graph() { return graph; }
    public EventLog log() { return log; }
    public Workspace workspace() { return workspace; }
    public MetricsCollector metrics() { return metrics; }
    public RunStatus status() { return status; }
    public Instant startedAt() { return startedAt; }
    public Instant finishedAt() { return finishedAt; }
    public String stopReason() { return stopReason.get(); }

    void status(RunStatus s) { this.status = s; }
    void markStarted() { this.startedAt = Instant.now(); this.status = RunStatus.RUNNING; }
    void markFinished(RunStatus s) { this.finishedAt = Instant.now(); this.status = s; }
    List<ScheduledChange> scheduledChanges() { return scheduledChanges; }
    Map<String, Set<String>> outputsByNode() { return outputsByNode; }
    Set<String> invalidated() { return invalidated; }

    /** Queues a requirement change; the coordinator applies it on its next tick (dynamic re-planning). */
    public void requestChange(ScheduledChange change) { scheduledChanges.add(change); }

    /** Asks the coordinator to safe-stop the run. */
    public void requestStop(String reason) {
        stopReason.compareAndSet(null, reason);
        stopRequested.set(true);
    }

    boolean stopping() { return stopRequested.get(); }

    /** Called by the RunManager once the run ended and its evidence has been exported. */
    void close() { closed.countDown(); }

    /** Waits until the run has ended and its report was written; false on timeout. */
    public boolean awaitClosed(java.time.Duration timeout) throws InterruptedException {
        return closed.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public boolean finished() { return status == RunStatus.COMPLETED || status == RunStatus.STOPPED; }

    public MetricsSnapshot metricsSnapshot() {
        return metrics.snapshot(graph, status, startedAt, finishedAt);
    }

    public RunInfo info() {
        return new RunInfo(id, scenario, graph.summaries(), metricsSnapshot());
    }
}
