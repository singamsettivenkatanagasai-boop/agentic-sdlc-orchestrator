package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.agents.Agent;
import com.agentic.orchestrator.agents.AgentRegistry;
import com.agentic.orchestrator.agents.AgentResult;
import com.agentic.orchestrator.agents.AgentTask;
import com.agentic.orchestrator.audit.EventType;
import com.agentic.orchestrator.context.ArtifactRef;
import com.agentic.orchestrator.context.RunContext;
import com.agentic.orchestrator.context.RunContext.Registration;
import com.agentic.orchestrator.engine.Completion.Outcome;
import com.agentic.orchestrator.governance.ApprovalService;
import com.agentic.orchestrator.governance.ApprovalService.Decision;
import com.agentic.orchestrator.governance.ApprovalService.Kind;
import com.agentic.orchestrator.governance.ApprovalService.Request;
import com.agentic.orchestrator.governance.AutonomyBoundaries;
import com.agentic.orchestrator.governance.PolicyDecision;
import com.agentic.orchestrator.governance.PolicyEngine;
import com.agentic.orchestrator.governance.PolicyOutcome;
import com.agentic.orchestrator.graph.Gate;
import com.agentic.orchestrator.graph.GateContext;
import com.agentic.orchestrator.graph.GateResult;
import com.agentic.orchestrator.graph.NodeState;
import com.agentic.orchestrator.graph.TaskNode;
import com.agentic.orchestrator.graph.WorkflowGraph;
import com.agentic.orchestrator.model.MetricsSnapshot;
import com.agentic.orchestrator.model.PolicyViolationException;
import com.agentic.orchestrator.model.RunOptions.ScheduledChange;
import com.agentic.orchestrator.model.RunStatus;
import com.agentic.orchestrator.planner.Planner;
import com.agentic.orchestrator.workspace.GuardedWorkspace;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The orchestration engine.
 *
 * <h3>Threading model</h3>
 * One <b>coordinator</b> loop (the calling thread) owns every decision that changes the shape of
 * the run: accepting results, expanding the plan, re-planning and safe-stopping. Each ready node
 * runs on its own <b>virtual-thread worker</b>, so independent branches execute in parallel and
 * a join node simply becomes ready once all its dependencies are DONE. Workers handle the
 * per-node reliability loop (retry with backoff, rollback, fallback agent, approvals) and hand a
 * {@link Completion} back; they never mark a node DONE themselves. This keeps re-planning free
 * of races: a result computed from superseded inputs is discarded by the coordinator.
 *
 * <h3>Failure handling, in order</h3>
 * <ol>
 *   <li>gate/agent/policy failure: undo the attempt's file changes (rollback), retry with the
 *       failure as feedback, exponential backoff, up to {@code maxAttempts}</li>
 *   <li>retry budget used up: switch to the node's fallback agent (if any)</li>
 *   <li>still failing, or a <b>fatal</b> policy violation (secret, path escape): safe-stop - all
 *       nodes STOPPED, workspace restored to the last checkpoint, state kept for humans</li>
 * </ol>
 */
public final class RunEngine {
    private static final long POLL_MILLIS = 200;
    private static final int FALLBACK_ATTEMPTS = 2;
    private static final String RUN = "run";
    private static final String ORCH = "orchestrator";

    private final AgentRegistry agents;
    private final PolicyEngine policy;
    private final AutonomyBoundaries boundaries;
    private final ApprovalService approvals;
    private final Planner planner;

    public RunEngine(AgentRegistry agents, PolicyEngine policy, AutonomyBoundaries boundaries,
                     ApprovalService approvals, Planner planner) {
        this.agents = agents;
        this.policy = policy;
        this.boundaries = boundaries;
        this.approvals = approvals;
        this.planner = planner;
    }

    /** Runs the whole graph to completion or safe-stop. Blocks the calling thread. */
    public void execute(Run run) {
        new Coordinator(run).run();
    }

    // ------------------------------------------------------------------------------------------
    // Coordinator
    // ------------------------------------------------------------------------------------------

    private final class Coordinator {
        private final Run run;
        private final WorkflowGraph graph;
        private final RunContext ctx;
        private final NodeStateMachine machine;
        private final BlockingQueue<Completion> done = new LinkedBlockingQueue<>();
        private final Set<String> inFlight = new LinkedHashSet<>();
        private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        private String lastCheckpoint;

        Coordinator(Run run) {
            this.run = run;
            this.graph = run.graph();
            this.ctx = run.context();
            this.machine = new NodeStateMachine(run.log());
        }

        void run() {
            run.markStarted();
            try {
                run.log().append(RUN, EventType.RUN_STARTED, ORCH,
                        run.scenario() + " | " + run.context().requirement());
                run.log().append(RUN, EventType.PLAN_CREATED, ORCH, describePlan());
                checkpoint("run-start");
                loop();
            } catch (RuntimeException e) {
                // Most likely the audit log could not be written (fail-closed) or a bug: stop everything.
                emergencyStop(e);
            } finally {
                workers.shutdownNow();
                try {
                    workers.awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void loop() {
            while (true) {
                if (run.stopping()) {
                    safeStop("operator requested stop: " + run.stopReason());
                    return;
                }
                for (TaskNode n : graph.readyNodes()) {
                    start(n);
                }
                if (inFlight.isEmpty()) {
                    if (graph.allDone()) {
                        finishCompleted();
                    } else {
                        safeStop("no node can run and the graph is not complete (blocked dependency)");
                    }
                    return;
                }
                Completion c;
                try {
                    c = done.poll(POLL_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    safeStop("coordinator interrupted");
                    return;
                }
                if (c != null) {
                    inFlight.remove(c.nodeId());
                    if (!handle(c)) {
                        return;
                    }
                }
                applyDueChanges();
            }
        }

        /** Marks the node READY and hands it to a worker thread. */
        private void start(TaskNode node) {
            machine.move(node, NodeState.READY, "all dependencies done " + node.dependsOn());
            inFlight.add(node.id());
            workers.submit(() -> {
                Completion c;
                try {
                    c = runNode(run, node, machine);
                } catch (Throwable t) {
                    c = Completion.failure(node.id(), Outcome.FATAL, node.agent(),
                            "unexpected error: " + t, 0, false);
                }
                done.add(c);
            });
        }

        /** @return false when the run has ended (safe-stopped) and the loop must exit. */
        private boolean handle(Completion c) {
            TaskNode node = graph.node(c.nodeId());
            switch (c.outcome()) {
                case SUCCESS -> {
                    accept(node, c);
                    return true;
                }
                case STOPPED -> {
                    if (run.stopping()) {
                        return true;
                    }
                    safeStop("worker for '" + node.id() + "' was interrupted");
                    return false;
                }
                case FATAL -> {
                    safeStop("fatal failure in '" + node.id() + "': " + c.reason());
                    return false;
                }
                default -> {
                    safeStop("retry budget exhausted for '" + node.id() + "' (fallback "
                            + (c.usedFallback() ? "also failed" : "not available") + "): " + c.reason());
                    return false;
                }
            }
        }

        // ---- accepting a result -----------------------------------------------------------

        private void accept(TaskNode node, Completion c) {
            if (run.invalidated().remove(node.id())) {
                // The inputs of this node were superseded while it was running: discard its output.
                c.attemptWorkspace().rollback();
                machine.move(node, NodeState.DONE, "result computed (about to be discarded)");
                machine.move(node, NodeState.STALE, "inputs changed by a re-plan while running");
                machine.move(node, NodeState.PENDING, "will run again with the new inputs");
                run.log().append(node.id(), EventType.REPLAN, ORCH, "discarded result computed from superseded inputs");
                return;
            }
            machine.move(node, NodeState.DONE, "gates passed"
                    + (c.approver() != null ? ", approved by " + c.approver() : ""));

            // Register every file this node produced; remember whether an earlier version changed.
            boolean changed = false;
            Map<String, String> outputs = new LinkedHashMap<>();
            Set<String> produced = new TreeSet<>();
            for (String path : c.changedPaths()) {
                if (!run.workspace().exists(path)) {
                    continue;
                }
                Registration reg = ctx.registerArtifact(path, run.workspace().read(path), node.id());
                outputs.put(path, reg.ref().sha256());
                produced.add(path);
                changed |= reg.changed();
            }
            run.outputsByNode().put(node.id(), produced);
            Map<String, String> inputs = new LinkedHashMap<>();
            for (String dep : node.dependsOn()) {
                for (String p : run.outputsByNode().getOrDefault(dep, Set.of())) {
                    ArtifactRef ref = ctx.artifact(p);
                    if (ref != null) {
                        inputs.put(p, ref.sha256());
                    }
                }
            }

            // Decision lineage: what the agent decided, then what the human decided.
            AgentResult r = c.result();
            record(node, c.agent() + (c.usedFallback() ? " (fallback)" : ""), "completed", r.rationale(), inputs, outputs);
            if (c.approver() != null) {
                record(node, c.approver(), "approved",
                        c.approvalComment() == null ? "approved" : c.approvalComment(), inputs, outputs);
            }
            if (node.humanInput()) {
                List<String> confirmed = c.answers().entrySet().stream()
                        .filter(Map.Entry::getValue).map(Map.Entry::getKey).sorted().toList();
                ctx.putAttribute("confirmedInterpretations", confirmed);
                ctx.addAssumption("Scope confirmed by " + c.approver() + ": " + (confirmed.isEmpty() ? "nothing" : confirmed));
                record(node, c.approver(), "scope-confirmed", "confirmed interpretations: " + confirmed, inputs, outputs);
            }
            run.metrics().accepted(c.failedAttempts(), c.mttrMillis());
            checkpoint("after " + node.id());

            List<String> added = planner.expandAfter(node, run.scenario(), ctx, graph);
            if (!added.isEmpty()) {
                run.log().append(node.id(), EventType.PLAN_EXPANDED, ORCH,
                        "plan expanded after '" + node.id() + "': added " + added + " | " + describePlan());
            }
            if (changed) {
                // An earlier artifact version changed: everything that consumed it is out of date.
                invalidateDownstream(node, "artifact(s) of '" + node.id() + "' changed");
            }
        }

        private void record(TaskNode node, String actor, String decision, String rationale,
                            Map<String, String> inputs, Map<String, String> outputs) {
            var d = ctx.recordDecision(node.id(), actor, decision, rationale, inputs, outputs);
            run.log().append(node.id(), EventType.DECISION, actor, d.id() + " " + decision + ": " + rationale);
        }

        private void checkpoint(String label) {
            try {
                String id = run.workspace().checkpoint(label);
                lastCheckpoint = id;
                ctx.putAttribute("lastCheckpoint", id);
                run.log().append(RUN, EventType.CHECKPOINT, ORCH, label + " -> " + id);
            } catch (RuntimeException e) {
                run.log().append(RUN, EventType.CHECKPOINT, ORCH, label + " FAILED (continuing): " + e.getMessage());
            }
        }

        // ---- dynamic re-planning ------------------------------------------------------------

        /** Applies scripted or API-submitted requirement changes whose trigger node is done. */
        private void applyDueChanges() {
            for (ScheduledChange ch : new ArrayList<>(run.scheduledChanges())) {
                boolean due = ch.afterNode() == null
                        || (graph.has(ch.afterNode()) && graph.node(ch.afterNode()).state() == NodeState.DONE);
                if (due) {
                    run.scheduledChanges().remove(ch);
                    applyChange(ch);
                }
            }
        }

        private void applyChange(ScheduledChange ch) {
            if (!graph.has(ch.targetNode())) {
                run.log().append(RUN, EventType.REPLAN, ORCH, "change ignored, unknown node '" + ch.targetNode() + "'");
                return;
            }
            if (run.metrics().replans() >= run.options().maxReplans()) {
                run.log().append(RUN, EventType.REPLAN, ORCH,
                        "change DENIED: re-plan budget (" + run.options().maxReplans() + ") exhausted");
                return;
            }
            ctx.amend(ch.amendment());
            run.log().append(ch.targetNode(), EventType.REPLAN, "requester", "requirement change: " + ch.amendment());
            Set<String> affected = new LinkedHashSet<>();
            affected.add(ch.targetNode());
            affected.addAll(graph.downstreamOf(ch.targetNode()));
            invalidate(affected, "requirement change: " + ch.amendment());
            run.metrics().replan();
            ctx.recordDecision(ch.targetNode(), "requester", "requirement-changed", ch.amendment(), Map.of(), Map.of());
        }

        private void invalidateDownstream(TaskNode source, String reason) {
            Set<String> down = graph.downstreamOf(source.id());
            // Only nodes that still hold results based on the old artifact need action; a node that is
            // already invalidated (or not started yet) is covered by an earlier re-plan.
            boolean any = down.stream().anyMatch(id -> graph.node(id).state() == NodeState.DONE
                    || (inFlight.contains(id) && !run.invalidated().contains(id)));
            if (!any) {
                return;
            }
            if (run.metrics().replans() >= run.options().maxReplans()) {
                run.log().append(source.id(), EventType.REPLAN, ORCH, "automatic re-plan DENIED: budget exhausted");
                return;
            }
            invalidate(down, reason);
            run.metrics().replan();
        }

        private void invalidate(Collection<String> ids, String reason) {
            for (String id : ids) {
                TaskNode n = graph.node(id);
                if (inFlight.contains(id)) {
                    run.invalidated().add(id);
                    run.log().append(id, EventType.REPLAN, ORCH, "running node invalidated, its result will be discarded: " + reason);
                } else if (n.state() == NodeState.DONE) {
                    machine.move(n, NodeState.STALE, reason);
                    machine.move(n, NodeState.PENDING, "re-planned");
                    run.log().append(id, EventType.REPLAN, ORCH, "marked stale and re-queued: " + reason);
                }
            }
        }

        // ---- ending the run ------------------------------------------------------------------

        private void finishCompleted() {
            MetricsSnapshot m = finish(RunStatus.COMPLETED);
            run.log().append(RUN, EventType.RUN_FINISHED, ORCH, "COMPLETED " + summarize(m));
        }

        /**
         * Safe-stop: cancel waiting humans, interrupt workers, mark every unfinished node STOPPED
         * and restore the workspace to the last good checkpoint. Completed work stays recorded.
         */
        private void safeStop(String reason) {
            run.requestStop(reason);
            run.metrics().safeStop();
            run.log().append(RUN, EventType.SAFE_STOP, ORCH, reason);
            approvals.cancelAll(run.id());
            workers.shutdownNow();
            try {
                workers.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            for (TaskNode n : graph.nodes()) {
                if (n.state() != NodeState.DONE) {
                    machine.tryMove(n, NodeState.STOPPED, "safe-stop");
                }
            }
            if (lastCheckpoint != null) {
                try {
                    run.workspace().restore(lastCheckpoint);
                    run.log().append(RUN, EventType.ROLLBACK, ORCH, "workspace restored to checkpoint " + lastCheckpoint);
                } catch (RuntimeException e) {
                    run.log().append(RUN, EventType.ROLLBACK, ORCH, "workspace restore FAILED: " + e.getMessage());
                }
            }
            MetricsSnapshot m = finish(RunStatus.STOPPED);
            run.log().append(RUN, EventType.RUN_FINISHED, ORCH, "STOPPED " + summarize(m));
        }

        private void emergencyStop(RuntimeException cause) {
            run.requestStop("internal error: " + cause);
            approvals.cancelAll(run.id());
            workers.shutdownNow();
            for (TaskNode n : graph.nodes()) {
                try {
                    if (n.state() != NodeState.DONE) {
                        n.setState(NodeState.STOPPED);
                    }
                } catch (RuntimeException ignored) {
                    // best effort
                }
            }
            run.markFinished(RunStatus.STOPPED);
        }

        private MetricsSnapshot finish(RunStatus status) {
            run.markFinished(status);
            return run.metricsSnapshot();
        }

        private String describePlan() {
            StringBuilder sb = new StringBuilder();
            for (TaskNode n : graph.nodes()) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(n.id()).append(n.dependsOn().isEmpty() ? "" : "<-" + n.dependsOn());
            }
            return sb.toString();
        }

        private String summarize(MetricsSnapshot m) {
            return String.format("nodes %d/%d, retries %d, rollbacks %d, fallbacks %d, safe-stops %d, replans %d, approvals %d, %d ms",
                    m.doneNodes(), m.totalNodes(), m.retries(), m.rollbacks(), m.fallbacks(), m.safeStops(),
                    m.replans(), m.approvals(), m.endToEndMillis());
        }
    }

    // ------------------------------------------------------------------------------------------
    // Worker: executes one node, including retries, fallback, rollback and approvals
    // ------------------------------------------------------------------------------------------

    private Completion runNode(Run run, TaskNode node, NodeStateMachine machine) {
        RunContext ctx = run.context();
        RetryPolicy backoff = new RetryPolicy(run.options().backoffBaseMillis());
        String agentName = node.agent();
        boolean usedFallback = false;
        int budget = node.maxAttempts();
        int attemptsInPhase = 0;
        int failed = 0;
        Instant firstFailure = null;
        String feedback = null;

        machine.move(node, NodeState.RUNNING, "started");
        Gate entry = node.entryGate();
        GateResult er = entry.evaluate(new GateContext(ctx, run.workspace(), node, run.graph()));
        run.log().append(node.id(), EventType.GATE, ORCH, "entry gate '" + entry.name() + "': "
                + (er.passed() ? "passed" : "FAILED - " + er.reason()));
        if (!er.passed()) {
            return Completion.failure(node.id(), Outcome.FATAL, agentName,
                    "entry gate '" + entry.name() + "' failed: " + er.reason(), 0, false);
        }

        while (true) {
            if (run.stopping()) {
                return Completion.failure(node.id(), Outcome.STOPPED, agentName, "run is stopping", failed, usedFallback);
            }
            attemptsInPhase++;
            int attemptNo = node.incrementAttempts();
            GuardedWorkspace gw = new GuardedWorkspace(run.workspace(), agentName,
                    boundaries.forAgent(agentName), policy);
            Throwable failure;
            try {
                return attempt(run, node, machine, agentName, gw, attemptNo, feedback, failed,
                        firstFailure, usedFallback);
            } catch (InterruptedException | CancellationException e) {
                gw.rollback();
                return Completion.failure(node.id(), Outcome.STOPPED, agentName, "interrupted", failed, usedFallback);
            } catch (TimeoutException e) {
                gw.rollback();
                run.log().append(node.id(), EventType.APPROVAL_DECIDED, ORCH, "no human decision before the timeout");
                return Completion.failure(node.id(), Outcome.FATAL, agentName,
                        "no human decision within " + run.options().approvalTimeout(), failed, usedFallback);
            } catch (PolicyViolationException e) {
                run.log().append(node.id(), EventType.POLICY, ORCH, "DENY [" + e.rule() + "] " + e.getMessage()
                        + (e.fatal() ? " (fatal)" : ""));
                if (e.fatal()) {
                    gw.rollback();
                    run.metrics().rollback();
                    return Completion.failure(node.id(), Outcome.FATAL, agentName,
                            "policy " + e.rule() + ": " + e.getMessage(), failed, usedFallback);
                }
                failure = e;
            } catch (Exception e) {
                failure = e;
            }

            // ---- failure path: record, roll back, then retry / fallback / give up -------------
            failed++;
            if (firstFailure == null) {
                firstFailure = Instant.now();
            }
            feedback = String.valueOf(failure.getMessage());
            run.log().append(node.id(), EventType.AGENT, agentName,
                    "attempt " + attemptNo + " FAILED: " + abbreviate(feedback));
            if (node.state() == NodeState.RUNNING) {
                machine.move(node, NodeState.FAILED, "attempt " + attemptNo + " failed");
            }
            gw.rollback();
            run.metrics().rollback();
            run.log().append(node.id(), EventType.ROLLBACK, ORCH, "undid the file changes of attempt " + attemptNo);
            machine.move(node, NodeState.ROLLED_BACK, "attempt " + attemptNo + " rolled back");

            if (attemptsInPhase < budget) {
                run.metrics().retry();
                long delay = backoff.delayBefore(attemptsInPhase + 1);
                run.log().append(node.id(), EventType.RETRY, ORCH,
                        "retry " + (attemptsInPhase + 1) + "/" + budget + " of " + agentName + " after " + delay + " ms");
                if (!sleep(delay)) {
                    return Completion.failure(node.id(), Outcome.STOPPED, agentName, "interrupted", failed, usedFallback);
                }
            } else if (!usedFallback && node.fallbackAgent() != null) {
                usedFallback = true;
                agentName = node.fallbackAgent();
                attemptsInPhase = 0;
                budget = FALLBACK_ATTEMPTS;
                run.metrics().fallback();
                run.log().append(node.id(), EventType.FALLBACK, ORCH,
                        "primary agent exhausted its " + node.maxAttempts() + " attempts; switching to '" + agentName + "'");
            } else {
                return Completion.failure(node.id(), Outcome.EXHAUSTED, agentName, abbreviate(feedback), failed, usedFallback);
            }
            machine.move(node, NodeState.RETRYING, "retrying with " + agentName);
            machine.move(node, NodeState.RUNNING, "attempt " + (node.attempts() + 1));
        }
    }

    /** One attempt: run the agent, check the exit gate, apply change-control policy, ask humans. */
    private Completion attempt(Run run, TaskNode node, NodeStateMachine machine, String agentName,
                               GuardedWorkspace gw, int attemptNo, String feedback, int failed,
                               Instant firstFailure, boolean usedFallback) throws Exception {
        RunContext ctx = run.context();
        Agent agent = agents.get(agentName);
        AgentTask task = new AgentTask(node, ctx, gw, run.options(), feedback, attemptNo, run::info);
        AgentResult result = agent.execute(task);
        run.log().append(node.id(), EventType.AGENT, agentName, "attempt " + attemptNo + ": " + result.summary());

        // Exit gate on the real workspace so a build/test sees every file, not just this agent's.
        Gate exit = node.exitGate();
        GateResult gr = exit.evaluate(new GateContext(ctx, run.workspace(), node, run.graph()));
        run.log().append(node.id(), EventType.GATE, ORCH, "exit gate '" + exit.name() + "': "
                + (gr.passed() ? "passed" : "FAILED - " + abbreviate(gr.reason())));
        if (!gr.passed()) {
            throw new GateFailedException(exit.name(), gr.reason());
        }

        String approver = null;
        String comment = null;
        Map<String, Boolean> answers = Map.of();

        if (node.humanInput()) {
            machine.move(node, NodeState.AWAITING_APPROVAL, "waiting for a human to clarify the requirement");
            Request req = new Request(run.id(), node.id(), Kind.CLARIFICATION,
                    "Which interpretations of the requirement are in scope? See docs/interpretations.md", Instant.now());
            run.log().append(node.id(), EventType.APPROVAL_REQUESTED, ORCH, req.summary());
            Decision d = approvals.await(req, run.options().approvalTimeout());
            run.metrics().approval();
            run.log().append(node.id(), EventType.APPROVAL_DECIDED, d.approver(),
                    (d.approved() ? "answered " : "declined ") + d.answers() + " " + nullToEmpty(d.comment()));
            if (!d.approved()) {
                throw new HumanRejectedException(d.approver(), nullToEmpty(d.comment()));
            }
            approver = d.approver();
            comment = d.comment();
            answers = d.answers();
        }

        PolicyDecision pd = policy.reviewNodeOutput(node, gw.changedPaths());
        if (pd.outcome() == PolicyOutcome.DENY) {
            throw new PolicyViolationException(pd.rule(), pd.reason(), pd.fatal());
        }
        if (pd.outcome() == PolicyOutcome.REQUIRE_APPROVAL && approver == null) {
            run.log().append(node.id(), EventType.POLICY, ORCH, "REQUIRE_APPROVAL [" + pd.rule() + "] " + pd.reason());
            machine.move(node, NodeState.AWAITING_APPROVAL, pd.reason());
            Request req = new Request(run.id(), node.id(), Kind.APPROVAL,
                    node.id() + ": " + result.summary() + " (" + pd.reason() + ")", Instant.now());
            run.log().append(node.id(), EventType.APPROVAL_REQUESTED, ORCH, req.summary());
            Decision d = approvals.await(req, run.options().approvalTimeout());
            run.metrics().approval();
            run.log().append(node.id(), EventType.APPROVAL_DECIDED, d.approver(),
                    (d.approved() ? "approved " : "REJECTED ") + nullToEmpty(d.comment()));
            if (!d.approved()) {
                throw new HumanRejectedException(d.approver(), nullToEmpty(d.comment()));
            }
            approver = d.approver();
            comment = d.comment();
        }
        long mttr = firstFailure == null ? -1 : Duration.between(firstFailure, Instant.now()).toMillis();
        return new Completion(node.id(), Outcome.SUCCESS, agentName, result, gw.changedPaths(), gw,
                approver, comment, answers, failed, mttr, usedFallback, "ok");
    }

    private static boolean sleep(long millis) {
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        String oneLine = s.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() > 300 ? oneLine.substring(0, 300) + "..." : oneLine;
    }
}
