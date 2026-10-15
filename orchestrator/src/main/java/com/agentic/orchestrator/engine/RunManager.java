package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.agents.GoldenBundle;
import com.agentic.orchestrator.audit.EventLog;
import com.agentic.orchestrator.governance.ApprovalService;
import com.agentic.orchestrator.governance.ApprovalService.Decision;
import com.agentic.orchestrator.governance.Responders;
import com.agentic.orchestrator.model.RunOptions.ScheduledChange;
import com.agentic.orchestrator.model.Scenario;
import com.agentic.orchestrator.planner.Planner;
import com.agentic.orchestrator.util.FileUtil;
import com.agentic.orchestrator.workspace.BuildRunner;
import com.agentic.orchestrator.workspace.CheckpointStore;
import com.agentic.orchestrator.workspace.LocalWorkspace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates, starts and tracks runs. There is exactly one working folder ({@code workspaces/demo}),
 * so only one run can be active at a time: greenfield builds in it, brownfield seeds it with the
 * baseline application and modifies it. Each run exports its evidence to {@code <exportRoot>/<label>}.
 */
public class RunManager {
    private final RunEngine engine;
    private final Planner planner;
    private final ApprovalService approvals;
    private final BuildRunner buildRunner;
    private final CheckpointStore checkpoints;
    private final GoldenBundle golden;
    private final MetricsSink metricsSink;
    private final Path workspaceRoot;
    private final Path exportRoot;
    private final RunExporter exporter = new RunExporter();

    private final Map<String, Run> runs = new ConcurrentHashMap<>();
    private Run active;

    public RunManager(RunEngine engine, Planner planner, ApprovalService approvals, BuildRunner buildRunner,
                      CheckpointStore checkpoints, GoldenBundle golden, MetricsSink metricsSink,
                      Path workspaceRoot, Path exportRoot) {
        this.engine = engine;
        this.planner = planner;
        this.approvals = approvals;
        this.buildRunner = buildRunner;
        this.checkpoints = checkpoints;
        this.golden = golden;
        this.metricsSink = metricsSink;
        this.workspaceRoot = workspaceRoot;
        this.exportRoot = exportRoot;
    }

    /** Starts a run asynchronously and returns it immediately. */
    public synchronized Run start(RunRequest req) {
        if (active != null && !active.finished()) {
            throw new IllegalStateException("run " + active.id() + " is still active; the working folder is shared");
        }
        String id = "run-" + UUID.randomUUID().toString().substring(0, 8);
        String label = req.label() == null || req.label().isBlank() ? id : req.label();
        Path exportDir = exportRoot.resolve(label);

        prepareWorkspace(req.scenario());
        LocalWorkspace ws = new LocalWorkspace(workspaceRoot, buildRunner, checkpoints);
        EventLog log = new EventLog(id, exportDir.resolve("events.jsonl"));
        Run run = new Run(id, label, req.scenario(), req.effectiveRequirement(), req.toOptions(), ws, log,
                planner.plan(req.scenario()), metricsSink);

        if (req.autoApprove()) {
            var human = Responders.simulatedHuman(req.clarificationAnswers() == null ? Map.of() : req.clarificationAnswers());
            if (req.rejectFirstNode() != null && !req.rejectFirstNode().isBlank()) {
                human = Responders.rejectFirst(req.rejectFirstNode(), human);
            }
            approvals.registerResponder(id, human);
        }
        runs.put(id, run);
        active = run;
        Thread.ofVirtual().name("run-" + id).start(() -> {
            try {
                engine.execute(run);
            } finally {
                try {
                    exporter.export(run, exportDir);
                } catch (RuntimeException e) {
                    System.err.println("could not export run " + id + ": " + e);
                }
                run.close();
            }
        });
        return run;
    }

    /** Resets the shared working folder: empty for greenfield, the baseline application for brownfield. */
    private void prepareWorkspace(Scenario scenario) {
        try {
            FileUtil.deleteRecursively(workspaceRoot);
            Files.createDirectories(workspaceRoot);
            if (scenario == Scenario.BROWNFIELD) {
                if (!golden.available()) {
                    throw new IllegalStateException("baseline application not found at " + golden.root());
                }
                LocalWorkspace seed = new LocalWorkspace(workspaceRoot, buildRunner, checkpoints);
                golden.allFiles().forEach(seed::write);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<Run> find(String id) { return Optional.ofNullable(runs.get(id)); }

    public List<Run> all() { return List.copyOf(runs.values()); }

    /** Operator stop: the run safe-stops within one coordinator tick. */
    public boolean stop(String id, String reason) {
        Run r = runs.get(id);
        if (r == null || r.finished()) {
            return false;
        }
        r.requestStop(reason);
        approvals.cancelAll(id);
        return true;
    }

    public List<ApprovalService.Request> pending(String runId) {
        return approvals.pendingFor(runId);
    }

    public boolean decide(String runId, String nodeId, Decision decision) {
        return approvals.submit(runId, nodeId, decision);
    }

    public boolean requestChange(String id, ScheduledChange change) {
        Run r = runs.get(id);
        if (r == null || r.finished()) {
            return false;
        }
        r.requestChange(change);
        return true;
    }
}
