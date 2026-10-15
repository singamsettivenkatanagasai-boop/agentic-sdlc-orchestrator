package com.agentic.orchestrator.support;

import com.agentic.orchestrator.agents.Agent;
import com.agentic.orchestrator.agents.AgentRegistry;
import com.agentic.orchestrator.agents.ArchitectAgent;
import com.agentic.orchestrator.agents.DeveloperAgent;
import com.agentic.orchestrator.agents.DocsAgent;
import com.agentic.orchestrator.agents.GoldenBundle;
import com.agentic.orchestrator.agents.GoldenDeveloperAgent;
import com.agentic.orchestrator.agents.ReleaseAgent;
import com.agentic.orchestrator.agents.RequirementsAgent;
import com.agentic.orchestrator.agents.ReviewerAgent;
import com.agentic.orchestrator.agents.ScopePlannerAgent;
import com.agentic.orchestrator.agents.SecurityAgent;
import com.agentic.orchestrator.agents.TestAgent;
import com.agentic.orchestrator.analysis.ImpactAnalyzer;
import com.agentic.orchestrator.audit.EventLog;
import com.agentic.orchestrator.config.RepoLocator;
import com.agentic.orchestrator.engine.MetricsSink;
import com.agentic.orchestrator.engine.Run;
import com.agentic.orchestrator.engine.RunEngine;
import com.agentic.orchestrator.engine.RunManager;
import com.agentic.orchestrator.governance.ApprovalService;
import com.agentic.orchestrator.governance.AutonomyBoundaries;
import com.agentic.orchestrator.governance.PolicyEngine;
import com.agentic.orchestrator.graph.WorkflowGraph;
import com.agentic.orchestrator.llm.ReplayLlmClient;
import com.agentic.orchestrator.model.RunOptions;
import com.agentic.orchestrator.model.Scenario;
import com.agentic.orchestrator.planner.Planner;
import com.agentic.orchestrator.workspace.JGitCheckpointStore;
import com.agentic.orchestrator.workspace.LocalWorkspace;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Wires the orchestrator the same way {@code AppConfig} does, without Spring, for fast tests. */
public final class Harness {
    public final FakeBuildRunner build = new FakeBuildRunner();
    public final ApprovalService approvals = new ApprovalService();
    public final PolicyEngine policy = new PolicyEngine();
    public final AutonomyBoundaries boundaries = new AutonomyBoundaries();
    public final Planner planner = new Planner();
    public final GoldenBundle golden = new GoldenBundle(RepoLocator.resolve("target-apps/url-shortener"));
    public final Path workspace;
    public final Path exports;

    public Harness(Path tmp) {
        this.workspace = tmp.resolve("ws");
        this.exports = tmp.resolve("runs");
    }

    /** The full set of real agents with replay (no language model). */
    public AgentRegistry realAgents() {
        ImpactAnalyzer analyzer = new ImpactAnalyzer();
        GoldenDeveloperAgent goldenDev = new GoldenDeveloperAgent(golden);
        return new AgentRegistry(List.of(
                new RequirementsAgent(), new ArchitectAgent(analyzer),
                new DeveloperAgent(new ReplayLlmClient(), goldenDev), goldenDev,
                new TestAgent(new ReplayLlmClient(), golden), new SecurityAgent(policy), new DocsAgent(analyzer),
                new ReleaseAgent(), new ReviewerAgent(), new ScopePlannerAgent()));
    }

    public RunEngine engine(AgentRegistry agents) {
        return new RunEngine(agents, policy, boundaries, approvals, planner);
    }

    public RunManager manager() {
        return new RunManager(engine(realAgents()), planner, approvals, build, new JGitCheckpointStore(), golden,
                MetricsSink.NOOP, workspace, exports);
    }

    /** A run over a hand-made graph, for engine tests with scripted agents. */
    public Run miniRun(WorkflowGraph graph, RunOptions options) {
        LocalWorkspace ws = new LocalWorkspace(workspace, build, new JGitCheckpointStore());
        String id = "test-" + Long.toHexString(System.nanoTime());
        return new Run(id, id, Scenario.GREENFIELD, "test requirement", options, ws, new EventLog(id, null),
                graph, MetricsSink.NOOP);
    }

    public static RunOptions fastOptions() {
        return RunOptions.defaults().withBackoffBaseMillis(1).withApprovalTimeout(Duration.ofSeconds(20));
    }
}
