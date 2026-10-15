package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.audit.EventLog;
import com.agentic.orchestrator.audit.EventType;
import com.agentic.orchestrator.audit.RunEvent;
import com.agentic.orchestrator.context.DecisionRecord;
import com.agentic.orchestrator.graph.NodeState;
import com.agentic.orchestrator.graph.TaskNode;
import com.agentic.orchestrator.model.MetricsSnapshot;
import com.agentic.orchestrator.util.Md;

import java.util.List;
import java.util.Locale;

/**
 * Renders the evidence report of a run: outcome, reliability metrics, the dependency graph
 * (Mermaid), a timeline of the notable audit events, decisions and artefacts, and whether the
 * audit hash chain verifies. The report is what a reviewer reads instead of the raw event log.
 */
public final class ReportGenerator {

    public String render(Run run) {
        MetricsSnapshot m = run.metricsSnapshot();
        List<RunEvent> events = run.log().events();
        StringBuilder r = new StringBuilder();
        r.append("# Run report: ").append(run.label()).append("\n\n");
        r.append("- Run id: `").append(run.id()).append("`\n");
        r.append("- Scenario: **").append(run.scenario()).append("**\n");
        r.append("- Outcome: **").append(run.status()).append("**");
        if (run.status() == com.agentic.orchestrator.model.RunStatus.STOPPED) {
            r.append(" (").append(run.stopReason()).append(")");
        }
        r.append("\n- Requirement: ").append(run.context().requirement()).append("\n");
        r.append("- Audit chain: ").append(EventLog.verify(events) ? "verified" : "BROKEN")
                .append(" (").append(events.size()).append(" events)\n\n");

        r.append("## Reliability metrics\n\n");
        r.append(Md.table(List.of("Metric", "Value"), List.of(
                List.of("Nodes completed", m.doneNodes() + " / " + m.totalNodes()),
                List.of("First-attempt success rate", String.format(Locale.ROOT, "%.0f%%", m.firstAttemptSuccessRate() * 100)),
                List.of("Retries", String.valueOf(m.retries())),
                List.of("Rollbacks", String.valueOf(m.rollbacks())),
                List.of("Fallbacks", String.valueOf(m.fallbacks())),
                List.of("Safe-stops", String.valueOf(m.safeStops())),
                List.of("Re-plans", String.valueOf(m.replans())),
                List.of("Human approvals", String.valueOf(m.approvals())),
                List.of("Mean time to recovery", String.format(Locale.ROOT, "%.0f ms", m.avgMttrMillis())),
                List.of("End-to-end latency", m.endToEndMillis() + " ms"))));

        r.append("\n## Dependency graph\n\n```mermaid\ngraph LR\n");
        for (TaskNode n : run.graph().nodes()) {
            r.append("  ").append(id(n.id())).append("[\"").append(n.id()).append("<br/>").append(n.state()).append("\"]\n");
            for (String d : n.dependsOn()) {
                r.append("  ").append(id(d)).append(" --> ").append(id(n.id())).append('\n');
            }
        }
        r.append("```\n\n## Nodes\n\n");
        r.append(Md.table(List.of("Node", "Agent", "Risk", "State", "Attempts", "Depends on"),
                run.graph().nodes().stream().map(n -> List.of(n.id(), n.agent(), n.risk().name(), n.state().name(),
                        String.valueOf(n.attempts()), n.dependsOn().toString())).toList()));

        r.append("\n## Timeline (state changes omitted)\n\n");
        r.append(Md.table(List.of("#", "Type", "Node", "Actor", "Detail"),
                events.stream().filter(e -> e.type() != EventType.STATE_CHANGE)
                        .map(e -> List.of(String.valueOf(e.seq()), e.type().name(), String.valueOf(e.nodeId()),
                                e.actor(), shorten(e.detail()))).toList()));

        r.append("\n## Decisions\n\n");
        List<DecisionRecord> ds = run.context().decisions();
        r.append(ds.isEmpty() ? "_None._\n" : Md.table(List.of("Id", "Node", "Actor", "Decision", "Rationale"),
                ds.stream().map(d -> List.of(d.id(), d.nodeId(), d.actor(), d.decision(), shorten(d.rationale()))).toList()));

        r.append("\n## Artefacts\n\n");
        r.append(Md.table(List.of("Path", "Version", "Producer", "SHA-256"),
                run.context().artifacts().values().stream().map(a -> List.of(a.path(), String.valueOf(a.version()),
                        a.producerNode(), a.sha256().substring(0, 12))).toList()));

        long stopped = run.graph().nodes().stream().filter(n -> n.state() == NodeState.STOPPED).count();
        if (stopped > 0) {
            r.append("\n_").append(stopped).append(" node(s) were STOPPED by the safe-stop; the workspace was restored to the last checkpoint._\n");
        }
        return r.toString();
    }

    private static String id(String nodeId) { return nodeId.replaceAll("[^A-Za-z0-9]", "_"); }

    private static String shorten(String s) {
        if (s == null) {
            return "";
        }
        String one = s.replace('\n', ' ').replace('\r', ' ');
        return one.length() > 180 ? one.substring(0, 180) + "..." : one;
    }
}
