package com.agentic.orchestrator.agents;

import com.agentic.orchestrator.context.ArtifactRef;
import com.agentic.orchestrator.context.DecisionRecord;
import com.agentic.orchestrator.model.RunInfo;
import com.agentic.orchestrator.util.Md;

import java.util.List;

/**
 * Final reviewer: assembles the engineering summary that the human signs off on - what was
 * built, which decisions were taken by whom, which artefacts exist, and the remaining risks.
 */
public class ReviewerAgent implements Agent {
    @Override
    public String name() { return "reviewer"; }

    @Override
    public AgentResult execute(AgentTask t) {
        RunInfo info = t.runInfo().get();
        StringBuilder s = new StringBuilder("# Engineering summary\n\n");
        s.append("Scenario: **").append(info.scenario()).append("**\n\n");
        s.append("## Requirement\n\n> ").append(t.context().requirement()).append("\n\n");
        if (!t.context().amendments().isEmpty()) {
            s.append("Amendments:\n\n").append(Md.bullets(t.context().amendments())).append('\n');
        }
        s.append("## Work breakdown\n\n");
        s.append(Md.table(List.of("Node", "Agent", "Risk", "State", "Attempts"),
                info.nodes().stream().map(n -> List.of(n.id(), n.agent(), n.risk().name(),
                        n.id().equals(t.node().id()) ? "AWAITING SIGN-OFF" : n.state().name(),
                        String.valueOf(n.attempts()))).toList()));
        s.append("\n## Decisions and approvals\n\n");
        List<DecisionRecord> ds = t.context().decisions();
        s.append(ds.isEmpty() ? "_None._\n" : Md.table(List.of("Id", "Node", "Actor", "Decision", "Rationale"),
                ds.stream().map(d -> List.of(d.id(), d.nodeId(), d.actor(), d.decision(), d.rationale())).toList()));
        s.append("\n## Assumptions\n\n").append(Md.bullets(t.context().assumptions())).append('\n');
        s.append("## Artefacts\n\n");
        s.append(Md.table(List.of("Path", "Version", "Producer", "SHA-256"),
                t.context().artifacts().values().stream()
                        .map(a -> List.of(a.path(), String.valueOf(a.version()), a.producerNode(), AgentSupport.shortHash(a.sha256())))
                        .toList()));
        s.append("\n## Remaining risks\n\n").append(Md.bullets(List.of(
                "No authentication; per-instance rate limiting; in-memory database",
                "Generated code is reviewed by gates and tests, not by formal verification")));
        t.workspace().write("docs/engineering-summary.md", s.toString());
        return new AgentResult("engineering summary written for human sign-off",
                "the reviewer only summarises recorded facts; it does not approve its own work");
    }

    @SuppressWarnings("unused")
    private static ArtifactRef unused() { return null; }
}
