package com.agentic.orchestrator.config;

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
import com.agentic.orchestrator.engine.MetricsSink;
import com.agentic.orchestrator.engine.RunEngine;
import com.agentic.orchestrator.engine.RunManager;
import com.agentic.orchestrator.governance.ApprovalService;
import com.agentic.orchestrator.governance.AutonomyBoundaries;
import com.agentic.orchestrator.governance.PolicyEngine;
import com.agentic.orchestrator.llm.AnthropicLlmClient;
import com.agentic.orchestrator.llm.LlmClient;
import com.agentic.orchestrator.llm.ReplayLlmClient;
import com.agentic.orchestrator.planner.Planner;
import com.agentic.orchestrator.workspace.BuildRunner;
import com.agentic.orchestrator.workspace.CheckpointStore;
import com.agentic.orchestrator.workspace.JGitCheckpointStore;
import com.agentic.orchestrator.workspace.MavenBuildRunner;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Wires the orchestrator. Everything here is plain constructor wiring; there is no hidden magic. */
@Configuration
public class AppConfig {

    @Bean
    PolicyEngine policyEngine() { return new PolicyEngine(); }

    @Bean
    AutonomyBoundaries autonomyBoundaries() { return new AutonomyBoundaries(); }

    @Bean
    ApprovalService approvalService() { return new ApprovalService(); }

    @Bean
    Planner planner() { return new Planner(); }

    @Bean
    ImpactAnalyzer impactAnalyzer() { return new ImpactAnalyzer(); }

    @Bean
    GoldenBundle goldenBundle() {
        return new GoldenBundle(RepoLocator.resolve("target-apps/url-shortener"));
    }

    /** Live model when an API key is configured, otherwise deterministic replay of the verified implementation. */
    @Bean
    LlmClient llmClient(OrchestratorProperties props) {
        String key = props.getLlm().getApiKey();
        if (key == null || key.isBlank()) {
            return new ReplayLlmClient();
        }
        OrchestratorProperties.Llm l = props.getLlm();
        return new AnthropicLlmClient(key, l.getModel(), l.getMaxTokens(), l.getEndpoint());
    }

    @Bean
    GoldenDeveloperAgent goldenDeveloperAgent(GoldenBundle golden) { return new GoldenDeveloperAgent(golden); }

    @Bean
    AgentRegistry agentRegistry(LlmClient llm, GoldenBundle golden, GoldenDeveloperAgent goldenDeveloper,
                                ImpactAnalyzer analyzer, PolicyEngine policy) {
        List<Agent> agents = List.of(
                new RequirementsAgent(),
                new ArchitectAgent(analyzer),
                new DeveloperAgent(llm, goldenDeveloper),
                goldenDeveloper,
                new TestAgent(llm, golden),
                new SecurityAgent(policy),
                new DocsAgent(analyzer),
                new ReleaseAgent(),
                new ReviewerAgent(),
                new ScopePlannerAgent());
        return new AgentRegistry(agents);
    }

    @Bean
    RunEngine runEngine(AgentRegistry agents, PolicyEngine policy, AutonomyBoundaries boundaries,
                        ApprovalService approvals, Planner planner) {
        return new RunEngine(agents, policy, boundaries, approvals, planner);
    }

    @Bean
    BuildRunner buildRunner(OrchestratorProperties props) {
        String command = props.getMavenCommand().isBlank() ? MavenBuildRunner.defaultCommand() : props.getMavenCommand();
        return new MavenBuildRunner(command, props.isMavenOffline(),
                Duration.ofMinutes(props.getMavenTimeoutMinutes()));
    }

    @Bean
    CheckpointStore checkpointStore() { return new JGitCheckpointStore(); }

    /** Publishes reliability metrics to Micrometer (visible at /actuator/metrics/agentic.*). */
    @Bean
    MetricsSink metricsSink(MeterRegistry registry) {
        return new MetricsSink() {
            @Override
            public void count(String name) { registry.counter(name).increment(); }

            @Override
            public void timer(String name, long millis) { registry.timer(name).record(Duration.ofMillis(millis)); }
        };
    }

    @Bean
    RunManager runManager(RunEngine engine, Planner planner, ApprovalService approvals, BuildRunner buildRunner,
                          CheckpointStore checkpoints, GoldenBundle golden, MetricsSink sink,
                          OrchestratorProperties props) {
        Path workspace = RepoLocator.resolve(props.getWorkspaceDir());
        Path exports = RepoLocator.resolve(props.getExportDir());
        return new RunManager(engine, planner, approvals, buildRunner, checkpoints, golden, sink, workspace, exports);
    }
}
