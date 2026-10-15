package com.agentic.orchestrator.agents;

import com.agentic.orchestrator.analysis.ImpactAnalyzer;
import com.agentic.orchestrator.analysis.ImpactAnalyzer.ClassInfo;
import com.agentic.orchestrator.analysis.ImpactAnalyzer.CodeModel;
import com.agentic.orchestrator.model.Scenario;
import com.agentic.orchestrator.util.Md;
import com.agentic.orchestrator.workspace.Workspace;

import java.util.ArrayList;
import java.util.List;

/**
 * Documentation agent. The README is derived from the parsed code (real endpoints, real
 * features), not from the plan, so documentation cannot drift from the implementation.
 */
public class DocsAgent implements Agent {
    private final ImpactAnalyzer analyzer;

    public DocsAgent(ImpactAnalyzer analyzer) { this.analyzer = analyzer; }

    @Override
    public String name() { return "docs"; }

    @Override
    public AgentResult execute(AgentTask t) {
        Workspace ws = t.workspace();
        CodeModel model = analyzer.scan(ws);
        boolean expiry = model.ofKind("entity").stream().anyMatch(c -> c.details().contains("expiresAt"));
        boolean rate = model.classes().stream().anyMatch(c -> c.name().equals("RateLimitFilter"));

        StringBuilder r = new StringBuilder("# URL Shortener\n\n");
        r.append(t.context().requirement()).append("\n\n");
        r.append("## API\n\n");
        r.append(Md.table(List.of("Method", "Path", "Handler"),
                model.endpoints().stream().map(e -> List.of(e.method(), e.path().replaceAll("\\{(\\w+):[^}]*\\}", "{$1}"), e.handler())).toList()));
        r.append("\nThe full contract is in `openapi.yaml`.\n\n");
        r.append("## Features\n\n");
        List<String> features = new ArrayList<>(List.of(
                "Short links with optional custom alias; the same URL always maps to the same link",
                "HTTP 302 redirects with click tracking and statistics",
                "SSRF-safe URL validation",
                "RFC 7807 problem responses"));
        if (expiry) {
            features.add("Optional link expiry (`expiresAt`); expired links answer 410 Gone");
        }
        if (rate) {
            features.add("Per-IP rate limiting; excess requests answer 429 with Retry-After");
        }
        r.append(Md.bullets(features)).append('\n');
        r.append("## Run and test\n\n```\nmvn spring-boot:run\nmvn test\n```\n\n");
        r.append("Classes: ").append(model.classes().size()).append(", controllers: ")
                .append(model.ofKind("controller").stream().map(ClassInfo::name).toList()).append("\n\n");
        r.append("See `docs/design.md` for the design and `docs/security-review.md` for the security review.\n");
        ws.write("README.md", r.toString());

        String existing = AgentSupport.readOrEmpty(ws, "docs/CHANGELOG.md");
        String entry;
        String header;
        if (t.context().scenario() == Scenario.BROWNFIELD) {
            header = "## 0.2.0 - link expiry and rate limiting";
            entry = header + "\n\n- Added: " + (expiry ? "optional link expiry (HTTP 410)" : "n/a")
                    + "\n- Added: " + (rate ? "per-IP rate limiting (HTTP 429)" : "n/a") + "\n- No breaking API changes\n";
        } else {
            header = "## 0.1.0 - initial release";
            entry = header + "\n\n- Create, redirect, details, delete and statistics endpoints\n- SSRF-safe validation\n";
        }
        String changelog = existing.contains(header) ? existing
                : "# Changelog\n\n" + entry + (existing.isEmpty() ? "" : "\n" + existing.replace("# Changelog\n\n", ""));
        ws.write("docs/CHANGELOG.md", changelog);
        return new AgentResult("README and changelog generated from " + model.endpoints().size() + " parsed endpoints",
                "documentation is derived from the code so it cannot drift from the implementation");
    }
}
