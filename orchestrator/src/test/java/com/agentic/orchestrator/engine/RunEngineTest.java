package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.agents.AgentRegistry;
import com.agentic.orchestrator.audit.EventLog;
import com.agentic.orchestrator.audit.EventType;
import com.agentic.orchestrator.governance.ApprovalService.Decision;
import com.agentic.orchestrator.governance.Responders;
import com.agentic.orchestrator.graph.Gates;
import com.agentic.orchestrator.graph.NodeState;
import com.agentic.orchestrator.graph.Risk;
import com.agentic.orchestrator.graph.TaskNode;
import com.agentic.orchestrator.graph.WorkflowGraph;
import com.agentic.orchestrator.model.MetricsSnapshot;
import com.agentic.orchestrator.model.RunOptions;
import com.agentic.orchestrator.model.RunOptions.ScheduledChange;
import com.agentic.orchestrator.model.RunStatus;
import com.agentic.orchestrator.support.Harness;
import com.agentic.orchestrator.support.ScriptedAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour of the engine with scripted agents: parallel branches and joins, retry with
 * rollback, fallback, safe-stop, human approval, rejection and re-planning. Scripted agents use
 * the names "docs" (writes docs/**), "developer" and "golden-developer" (write src/main/**)
 * because those are the autonomy boundaries the engine enforces.
 */
class RunEngineTest {
    @TempDir
    Path tmp;

    Harness h;

    @BeforeEach
    void setUp() {
        h = new Harness(tmp);
    }

    private static TaskNode docsNode(String id, String... deps) {
        return TaskNode.builder(id, "docs").dependsOn(deps).build();
    }

    private static WorkflowGraph graph(TaskNode... nodes) {
        WorkflowGraph g = new WorkflowGraph();
        for (TaskNode n : nodes) {
            g.add(n);
        }
        g.validate();
        return g;
    }

    /** A docs-agent that writes docs/&lt;node&gt;.md after an optional pause. */
    private static ScriptedAgent writer(AtomicInteger running, AtomicInteger maxRunning, Map<String, AtomicInteger> counts, long pauseMillis) {
        return new ScriptedAgent("docs", t -> {
            counts.computeIfAbsent(t.node().id(), k -> new AtomicInteger()).incrementAndGet();
            int now = running.incrementAndGet();
            maxRunning.accumulateAndGet(now, Math::max);
            try {
                if (pauseMillis > 0) {
                    Thread.sleep(pauseMillis);
                }
                t.workspace().write("docs/" + t.node().id() + ".md", "output of " + t.node().id());
            } finally {
                running.decrementAndGet();
            }
        });
    }

    private Run execute(WorkflowGraph g, AgentRegistry agents) {
        Run run = h.miniRun(g, Harness.fastOptions());
        h.engine(agents).execute(run);
        return run;
    }

    private static NodeState state(Run run, String id) { return run.graph().node(id).state(); }

    private static long events(Run run, EventType type) {
        return run.log().events().stream().filter(e -> e.type() == type).count();
    }

    @Test
    void independentBranchesRunInParallelAndTheJoinWaitsForBoth() {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger max = new AtomicInteger();
        Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        WorkflowGraph g = graph(docsNode("a"), docsNode("b", "a"), docsNode("c", "a"), docsNode("d", "b", "c"));
        Run run = execute(g, new AgentRegistry(List.of(writer(running, max, counts, 300))));

        assertEquals(RunStatus.COMPLETED, run.status());
        assertEquals(2, max.get(), "b and c must overlap");
        assertTrue(g.allDone());
        assertTrue(EventLog.verify(run.log().events()));
        MetricsSnapshot m = run.metricsSnapshot();
        assertEquals(4, m.doneNodes());
        assertEquals(1.0, m.firstAttemptSuccessRate());
    }

    @Test
    void failedAttemptIsRolledBackAndRetriedUntilItSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> feedback = new AtomicReference<>();
        ScriptedAgent flaky = new ScriptedAgent("docs", t -> {
            if (t.node().id().equals("b")) {
                t.workspace().write("docs/scratch-" + t.attempt() + ".md", "half-done");
                if (calls.incrementAndGet() < 3) {
                    throw new IllegalStateException("boom " + calls.get());
                }
                feedback.set(t.feedback());
            }
            t.workspace().write("docs/" + t.node().id() + ".md", "ok");
        });
        WorkflowGraph g = graph(docsNode("a"), docsNode("b", "a"));
        Run run = execute(g, new AgentRegistry(List.of(flaky)));

        assertEquals(RunStatus.COMPLETED, run.status());
        MetricsSnapshot m = run.metricsSnapshot();
        assertEquals(2, m.retries());
        assertEquals(2, m.rollbacks());
        assertEquals(0, m.fallbacks());
        assertEquals(0.5, m.firstAttemptSuccessRate(), "a succeeded first time, b did not");
        assertTrue(m.avgMttrMillis() >= 0);
        assertEquals(3, g.node("b").attempts());
        assertTrue(feedback.get().contains("boom"), "the retry receives the previous failure as feedback");
        assertFalse(run.workspace().exists("docs/scratch-1.md"), "files of failed attempts are rolled back");
        assertFalse(run.workspace().exists("docs/scratch-2.md"));
        assertTrue(run.workspace().exists("docs/scratch-3.md"), "the successful attempt keeps its files");
    }

    @Test
    void fallbackAgentTakesOverWhenThePrimaryIsExhausted() {
        ScriptedAgent primary = new ScriptedAgent("developer", t -> {
            t.workspace().write("src/main/java/Partial.java", "class Partial {");
            throw new IllegalStateException("cannot do it");
        });
        ScriptedAgent fallback = new ScriptedAgent("golden-developer",
                t -> t.workspace().write("src/main/java/Fixed.java", "class Fixed {}"));
        WorkflowGraph g = graph(TaskNode.builder("implement", "developer").maxAttempts(3)
                .fallbackAgent("golden-developer").build());
        Run run = execute(g, new AgentRegistry(List.of(primary, fallback)));

        assertEquals(RunStatus.COMPLETED, run.status());
        assertEquals(1, run.metricsSnapshot().fallbacks());
        assertEquals(4, g.node("implement").attempts());
        assertTrue(run.workspace().exists("src/main/java/Fixed.java"));
        assertFalse(run.workspace().exists("src/main/java/Partial.java"));
        assertEquals(1, events(run, EventType.FALLBACK));
    }

    @Test
    void exhaustedRetriesSafeStopTheRunAndKeepCompletedWork() {
        ScriptedAgent bad = new ScriptedAgent("developer", t -> {
            t.workspace().write("src/main/java/Partial.java", "class Partial {");
            throw new IllegalStateException("still broken");
        });
        WorkflowGraph g = graph(docsNode("a"),
                TaskNode.builder("b", "developer").dependsOn("a").maxAttempts(2).build(),
                docsNode("c", "b"));
        Run run = execute(g, new AgentRegistry(List.of(writer(new AtomicInteger(), new AtomicInteger(), new ConcurrentHashMap<>(), 0), bad)));

        assertEquals(RunStatus.STOPPED, run.status());
        assertEquals(NodeState.DONE, state(run, "a"));
        assertEquals(NodeState.STOPPED, state(run, "b"));
        assertEquals(NodeState.STOPPED, state(run, "c"));
        assertEquals(1, run.metricsSnapshot().safeStops());
        assertEquals(1, run.metricsSnapshot().retries());
        assertEquals(2, run.metricsSnapshot().rollbacks());
        assertFalse(run.workspace().exists("src/main/java/Partial.java"));
        assertTrue(run.workspace().exists("docs/a.md"), "work completed before the failure is kept");
        assertEquals(1, events(run, EventType.SAFE_STOP));
        assertTrue(EventLog.verify(run.log().events()));
    }

    @Test
    void aSecretIsAFatalPolicyViolationAndNeverReachesTheDisk() {
        ScriptedAgent leaky = new ScriptedAgent("developer", t -> t.workspace().write("src/main/resources/x.properties",
                "aws=" + "AKIA" + "IOSFODNN7EXAMPLE"));
        WorkflowGraph g = graph(docsNode("a"), TaskNode.builder("b", "developer").dependsOn("a").maxAttempts(3).build());
        Run run = execute(g, new AgentRegistry(List.of(writer(new AtomicInteger(), new AtomicInteger(), new ConcurrentHashMap<>(), 0), leaky)));

        assertEquals(RunStatus.STOPPED, run.status());
        assertTrue(run.stopReason().contains("no-secrets"), run.stopReason());
        assertEquals(1, g.node("b").attempts(), "fatal violations are not retried");
        assertFalse(run.workspace().exists("src/main/resources/x.properties"));
        assertEquals(NodeState.DONE, state(run, "a"));
    }

    @Test
    void highRiskNodePausesUntilAHumanApproves() throws Exception {
        WorkflowGraph g = graph(TaskNode.builder("design", "docs").risk(Risk.HIGH).build(), docsNode("after", "design"));
        Run run = h.miniRun(g, Harness.fastOptions());
        Thread t = Thread.ofVirtual().start(() -> h.engine(new AgentRegistry(List.of(
                writer(new AtomicInteger(), new AtomicInteger(), new ConcurrentHashMap<>(), 0)))).execute(run));

        long deadline = System.currentTimeMillis() + 10_000;
        while (h.approvals.pendingFor(run.id()).isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(1, h.approvals.pendingFor(run.id()).size(), "the run must wait for a human");
        assertEquals(NodeState.AWAITING_APPROVAL, state(run, "design"));
        assertEquals(NodeState.PENDING, state(run, "after"), "nothing downstream may start");
        assertEquals(RunStatus.RUNNING, run.status());

        assertTrue(h.approvals.submit(run.id(), "design", Decision.approve("human:alice", "looks good")));
        t.join(15_000);
        assertEquals(RunStatus.COMPLETED, run.status());
        assertTrue(run.context().decisions().stream().anyMatch(d ->
                d.nodeId().equals("design") && d.actor().equals("human:alice") && d.decision().equals("approved")));
        assertEquals(1, run.metricsSnapshot().approvals());
    }

    @Test
    void rejectedOutputIsReworkedWithTheReviewersComment() {
        AtomicReference<String> seen = new AtomicReference<>();
        ScriptedAgent agent = new ScriptedAgent("docs", t -> {
            seen.set(t.feedback());
            t.workspace().write("docs/" + t.node().id() + ".md", "attempt " + t.attempt());
        });
        WorkflowGraph g = graph(TaskNode.builder("design", "docs").risk(Risk.HIGH).build());
        Run run = h.miniRun(g, Harness.fastOptions());
        h.approvals.registerResponder(run.id(), Responders.rejectFirst("design", Responders.simulatedHuman(Map.of())));
        h.engine(new AgentRegistry(List.of(agent))).execute(run);

        assertEquals(RunStatus.COMPLETED, run.status());
        assertEquals(2, g.node("design").attempts());
        assertTrue(seen.get().contains("rejected"), "second attempt must see the rejection: " + seen.get());
        assertEquals("attempt 2", run.workspace().read("docs/design.md"));
        assertEquals(1, run.metricsSnapshot().retries());
        assertEquals(2, run.metricsSnapshot().approvals());
    }

    @Test
    void noHumanDecisionBeforeTheTimeoutSafeStopsTheRun() {
        WorkflowGraph g = graph(TaskNode.builder("design", "docs").risk(Risk.HIGH).build());
        Run run = h.miniRun(g, Harness.fastOptions().withApprovalTimeout(Duration.ofMillis(300)));
        h.engine(new AgentRegistry(List.of(writer(new AtomicInteger(), new AtomicInteger(), new ConcurrentHashMap<>(), 0)))).execute(run);
        assertEquals(RunStatus.STOPPED, run.status());
        assertTrue(run.stopReason().contains("human decision"), run.stopReason());
        assertEquals(NodeState.STOPPED, state(run, "design"));
    }

    @Test
    void failingEntryGateStopsTheRunBeforeTheAgentStarts() {
        AtomicInteger calls = new AtomicInteger();
        ScriptedAgent agent = new ScriptedAgent("docs", t -> calls.incrementAndGet());
        WorkflowGraph g = graph(TaskNode.builder("needs-input", "docs")
                .entryGate(Gates.requireArtifacts("docs/never-produced.md")).build());
        Run run = execute(g, new AgentRegistry(List.of(agent)));
        assertEquals(RunStatus.STOPPED, run.status());
        assertEquals(0, calls.get());
    }

    @Test
    void failingExitGateCountsAsAFailedAttempt() {
        AtomicInteger calls = new AtomicInteger();
        ScriptedAgent agent = new ScriptedAgent("docs", t -> {
            if (calls.incrementAndGet() >= 2) {
                t.workspace().write("docs/out.md", "now it exists");
            }
        });
        WorkflowGraph g = graph(TaskNode.builder("n", "docs").exitGate(Gates.producesFiles("docs/out.md")).build());
        Run run = execute(g, new AgentRegistry(List.of(agent)));
        assertEquals(RunStatus.COMPLETED, run.status());
        assertEquals(2, calls.get());
        assertEquals(1, run.metricsSnapshot().retries());
    }

    @Test
    void operatorStopInterruptsRunningWorkersQuickly() throws Exception {
        ScriptedAgent slow = new ScriptedAgent("docs", t -> Thread.sleep(30_000));
        WorkflowGraph g = graph(docsNode("slow"));
        Run run = h.miniRun(g, Harness.fastOptions());
        Thread t = Thread.ofVirtual().start(() -> h.engine(new AgentRegistry(List.of(slow))).execute(run));
        Thread.sleep(300);
        long t0 = System.currentTimeMillis();
        run.requestStop("operator");
        t.join(10_000);
        assertEquals(RunStatus.STOPPED, run.status());
        assertTrue(System.currentTimeMillis() - t0 < 5_000, "stop must not wait for the worker to finish");
        assertEquals(NodeState.STOPPED, state(run, "slow"));
    }

    @Test
    void requirementChangeStalesTheTargetAndEverythingDownstreamThenReRunsThem() {
        Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        WorkflowGraph g = graph(docsNode("a"), docsNode("b", "a"), docsNode("c", "b"));
        Run run = h.miniRun(g, Harness.fastOptions().withChange(new ScheduledChange("b", "a", "also support X")));
        h.engine(new AgentRegistry(List.of(writer(new AtomicInteger(), new AtomicInteger(), counts, 0)))).execute(run);

        assertEquals(RunStatus.COMPLETED, run.status());
        assertEquals(2, counts.get("a").get());
        assertEquals(2, counts.get("b").get());
        assertEquals(1, counts.get("c").get(), "c had not started yet, so it only runs once");
        assertEquals(1, run.metricsSnapshot().replans());
        assertTrue(run.context().amendments().contains("also support X"));
        assertTrue(events(run, EventType.REPLAN) >= 3);
    }

    @Test
    void aNodeThatFinishesAfterBeingInvalidatedIsDiscardedAndRerun() {
        Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        ScriptedAgent agent = new ScriptedAgent("docs", t -> {
            counts.computeIfAbsent(t.node().id(), k -> new AtomicInteger()).incrementAndGet();
            if (t.node().id().equals("slow")) {
                Thread.sleep(700);
            }
            t.workspace().write("docs/" + t.node().id() + ".md", "run " + counts.get(t.node().id()).get());
        });
        WorkflowGraph g = graph(docsNode("a"), docsNode("fast", "a"), docsNode("slow", "a"));
        Run run = h.miniRun(g, Harness.fastOptions().withChange(new ScheduledChange("fast", "a", "changed inputs")));
        h.engine(new AgentRegistry(List.of(agent))).execute(run);

        assertEquals(RunStatus.COMPLETED, run.status());
        assertEquals(2, counts.get("a").get());
        assertEquals(2, counts.get("slow").get(), "the first result of 'slow' used superseded inputs");
        assertEquals("run 2", run.workspace().read("docs/slow.md"));
        assertEquals(1, run.metricsSnapshot().replans());
    }

    @Test
    void replanBudgetIsEnforced() {
        Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        WorkflowGraph g = graph(docsNode("a"), docsNode("b", "a"));
        Run run = h.miniRun(g, Harness.fastOptions().withMaxReplans(0).withChange(new ScheduledChange("b", "a", "too late")));
        h.engine(new AgentRegistry(List.of(writer(new AtomicInteger(), new AtomicInteger(), counts, 0)))).execute(run);
        assertEquals(RunStatus.COMPLETED, run.status());
        assertEquals(1, counts.get("a").get());
        assertEquals(0, run.metricsSnapshot().replans());
        assertTrue(run.log().events().stream().anyMatch(e -> e.type() == EventType.REPLAN && e.detail().contains("DENIED")));
    }

    @Test
    void lineageTracesAnArtifactBackToItsInputs() {
        Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        WorkflowGraph g = graph(docsNode("a"), docsNode("b", "a"));
        Run run = execute(g, new AgentRegistry(List.of(writer(new AtomicInteger(), new AtomicInteger(), counts, 0))));
        LineageService.Trace trace = new LineageService().trace(run.context(), "docs/b.md");
        assertNotNull(trace);
        assertEquals("b", trace.producerNode());
        assertEquals(1, trace.inputs().size());
        assertEquals("docs/a.md", trace.inputs().get(0).path());
        assertFalse(trace.decisions().isEmpty());
    }
}
