package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.audit.RunEvent;
import com.agentic.orchestrator.context.DecisionRecord;
import com.agentic.orchestrator.graph.TaskNode;
import com.agentic.orchestrator.model.MetricsSnapshot;
import com.agentic.orchestrator.util.FileUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the evidence of a run to a directory: events.jsonl (audit log), graph.json,
 * decisions.json, metrics.json, report.md and a copy of the generated documents.
 * Only plain strings and numbers are serialised so no extra Jackson modules are needed.
 */
public final class RunExporter {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final ReportGenerator reports = new ReportGenerator();

    public void export(Run run, Path dir) {
        try {
            Files.createDirectories(dir);
            Path events = dir.resolve("events.jsonl");
            if (run.log().file() != null && !run.log().file().toAbsolutePath().equals(events.toAbsolutePath())
                    && Files.exists(run.log().file())) {
                Files.copy(run.log().file(), events, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            JSON.writeValue(dir.resolve("graph.json").toFile(), graph(run));
            JSON.writeValue(dir.resolve("decisions.json").toFile(), decisions(run));
            JSON.writeValue(dir.resolve("metrics.json").toFile(), metrics(run.metricsSnapshot()));
            Files.writeString(dir.resolve("report.md"), reports.render(run));
            copyDocuments(run, dir.resolve("artifacts"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copyDocuments(Run run, Path target) throws IOException {
        FileUtil.deleteRecursively(target);
        List<String> files = new ArrayList<>(run.workspace().list("docs/**"));
        for (String f : List.of("README.md", "openapi.yaml", "src/main/resources/db/migration/V2__add_link_expiry.sql")) {
            if (run.workspace().exists(f)) {
                files.add(f);
            }
        }
        for (String f : files) {
            Path out = target.resolve(f);
            Files.createDirectories(out.getParent());
            Files.writeString(out, run.workspace().read(f));
        }
    }

    private static List<Map<String, Object>> graph(Run run) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (TaskNode n : run.graph().nodes()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.id());
            m.put("agent", n.agent());
            m.put("fallbackAgent", n.fallbackAgent());
            m.put("risk", n.risk().name());
            m.put("state", n.state().name());
            m.put("attempts", n.attempts());
            m.put("dependsOn", n.dependsOn());
            m.put("entryGate", n.entryGate().name());
            m.put("exitGate", n.exitGate().name());
            m.put("description", n.description());
            nodes.add(m);
        }
        return nodes;
    }

    private static List<Map<String, Object>> decisions(Run run) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DecisionRecord d : run.context().decisions()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.id());
            m.put("node", d.nodeId());
            m.put("actor", d.actor());
            m.put("decision", d.decision());
            m.put("rationale", d.rationale());
            m.put("inputs", d.inputs());
            m.put("outputs", d.outputs());
            m.put("at", d.at().toString());
            out.add(m);
        }
        return out;
    }

    private static Map<String, Object> metrics(MetricsSnapshot s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", s.status().name());
        m.put("totalNodes", s.totalNodes());
        m.put("doneNodes", s.doneNodes());
        m.put("firstAttemptSuccessRate", s.firstAttemptSuccessRate());
        m.put("retries", s.retries());
        m.put("rollbacks", s.rollbacks());
        m.put("fallbacks", s.fallbacks());
        m.put("safeStops", s.safeStops());
        m.put("replans", s.replans());
        m.put("approvals", s.approvals());
        m.put("avgMttrMillis", s.avgMttrMillis());
        m.put("endToEndMillis", s.endToEndMillis());
        return m;
    }

    @SuppressWarnings("unused")
    private static void unused(RunEvent e) { }
}
