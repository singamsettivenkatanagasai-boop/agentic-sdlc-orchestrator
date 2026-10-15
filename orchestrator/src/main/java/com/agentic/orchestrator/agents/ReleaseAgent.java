package com.agentic.orchestrator.agents;

import com.agentic.orchestrator.context.DecisionRecord;
import com.agentic.orchestrator.graph.Risk;
import com.agentic.orchestrator.model.NodeSummary;
import com.agentic.orchestrator.model.RunInfo;
import com.agentic.orchestrator.graph.NodeState;
import com.agentic.orchestrator.util.Md;
import com.agentic.orchestrator.workspace.TestResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Release readiness: verifies that every upstream gate is green, tests and security review
 * passed, high-impact steps were approved by a human and a rollback plan exists. A failed
 * checklist fails the node; a human then approves the release itself.
 */
public class ReleaseAgent implements Agent {
    @Override
    public String name() { return "release"; }

    @Override
    public AgentResult execute(AgentTask t) {
        RunInfo info = t.runInfo().get();
        List<List<String>> rows = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        List<String> notDone = info.nodes().stream()
                .filter(n -> !Set.of("release", "signoff").contains(n.id()))
                .filter(n -> n.state() != NodeState.DONE).map(NodeSummary::id).toList();
        check(rows, failed, "All upstream nodes completed with passing gates", notDone.isEmpty(),
                notDone.isEmpty() ? "all done" : "not done: " + notDone);

        Object tr = t.context().attribute("testResult");
        boolean testsOk = tr instanceof TestResult r && r.success();
        check(rows, failed, "Tests pass", testsOk, tr instanceof TestResult r ? r.summary() : "no test result");

        boolean secOk = t.workspace().exists("docs/security-review.md")
                && t.workspace().read("docs/security-review.md").contains("Verdict: PASS");
        check(rows, failed, "Security review verdict is PASS", secOk, secOk ? "PASS" : "missing or failed");

        List<String> unapproved = new ArrayList<>();
        for (NodeSummary n : info.nodes()) {
            if (n.risk() == Risk.HIGH && !Set.of("release", "signoff").contains(n.id())) {
                boolean approved = t.context().decisions().stream()
                        .anyMatch(d -> d.nodeId().equals(n.id()) && d.decision().equals("approved"));
                if (!approved) {
                    unapproved.add(n.id());
                }
            }
        }
        check(rows, failed, "High-impact steps approved by a human", unapproved.isEmpty(),
                unapproved.isEmpty() ? "all approved" : "missing approval: " + unapproved);

        Object cp = t.context().attribute("lastCheckpoint");
        check(rows, failed, "Rollback plan available", cp != null, cp == null ? "no checkpoint" : "workspace checkpoint " + cp);

        StringBuilder n = new StringBuilder("# Release notes\n\n");
        n.append("Scenario: ").append(info.scenario()).append("\n\n## Readiness checklist\n\n");
        n.append(Md.table(List.of("Check", "Result", "Evidence"), rows)).append('\n');
        n.append("## Rollback plan\n\nRestore the workspace to checkpoint `").append(cp)
                .append("` (`git reset --hard <checkpoint>` inside the workspace) or revert the merge commit.\n\n");
        n.append("## Known limitations\n\n").append(Md.bullets(List.of(
                "No authentication", "Rate limiting is per instance", "In-memory database in the prototype")));
        t.workspace().write("docs/release-notes.md", n.toString());
        if (!failed.isEmpty()) {
            throw new IllegalStateException("release readiness failed: " + failed);
        }
        return new AgentResult("release readiness checklist passed (" + rows.size() + " checks)",
                "release is blocked unless every check passes; the human approves afterwards");
    }

    private static void check(List<List<String>> rows, List<String> failed, String name, boolean ok, String evidence) {
        rows.add(List.of(name, ok ? "PASS" : "FAIL", evidence));
        if (!ok) {
            failed.add(name);
        }
    }

    @SuppressWarnings("unused")
    private static List<DecisionRecord> none() { return List.of(); }
}
