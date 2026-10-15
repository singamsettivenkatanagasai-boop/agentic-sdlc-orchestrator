package com.agentic.orchestrator.agents;

import com.agentic.orchestrator.governance.PolicyEngine;
import com.agentic.orchestrator.model.PolicyViolationException;
import com.agentic.orchestrator.util.Md;
import com.agentic.orchestrator.workspace.Workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Security and compliance review of the generated project: secret scan, dependency allowlist,
 * dangerous API patterns and the presence of an SSRF guard. HIGH findings fail the node.
 */
public class SecurityAgent implements Agent {
    private record Check(Pattern pattern, String severity, String message) { }

    private record Finding(String severity, String where, String message) { }

    private static final List<Check> CHECKS = List.of(
            new Check(Pattern.compile("Runtime\\.getRuntime\\(\\)\\.exec|new ProcessBuilder"), "HIGH", "command execution found"),
            new Check(Pattern.compile("createNativeQuery\\([^)]*\\+"), "HIGH", "possible SQL injection (string-built native query)"),
            new Check(Pattern.compile("@CrossOrigin\\(\\s*(?:origins\\s*=\\s*)?\"\\*\""), "MEDIUM", "wildcard CORS origin"),
            new Check(Pattern.compile("exposure\\.include\\s*=\\s*\\*"), "MEDIUM", "all actuator endpoints exposed"),
            new Check(Pattern.compile("printStackTrace\\("), "LOW", "stack trace printed instead of logged"));

    private final PolicyEngine policy;

    public SecurityAgent(PolicyEngine policy) { this.policy = policy; }

    @Override
    public String name() { return "security"; }

    @Override
    public AgentResult execute(AgentTask t) {
        Workspace ws = t.workspace();
        List<String> files = new ArrayList<>(ws.list("src/**"));
        if (ws.exists("pom.xml")) {
            files.add("pom.xml");
        }
        List<Finding> findings = new ArrayList<>();
        boolean ssrfGuard = false;
        for (String f : files) {
            String content = ws.read(f);
            Optional<String> secret = policy.scanSecrets(content);
            if (secret.isPresent()) {
                throw new PolicyViolationException("no-secrets", "possible " + secret.get() + " in " + f, true);
            }
            for (Check c : CHECKS) {
                if (c.pattern().matcher(content).find()) {
                    findings.add(new Finding(c.severity(), f, c.message()));
                }
            }
            if (content.contains("isLoopbackAddress") && content.contains("isSiteLocalAddress")) {
                ssrfGuard = true;
            }
        }
        if (ws.exists("pom.xml")) {
            policy.checkDependencies(ws.read("pom.xml"))
                    .ifPresent(m -> findings.add(new Finding("HIGH", "pom.xml", m)));
        }
        boolean hasController = files.stream().anyMatch(f -> f.endsWith("Controller.java"));
        if (hasController && !ssrfGuard) {
            findings.add(new Finding("HIGH", "src/main", "no SSRF guard found for user-supplied URLs"));
        }
        long high = findings.stream().filter(f -> f.severity().equals("HIGH")).count();

        StringBuilder r = new StringBuilder("# Security review\n\n");
        r.append("Verdict: ").append(high == 0 ? "PASS" : "FAIL").append("\n\n");
        r.append("## Controls verified\n\n").append(Md.bullets(List.of(
                "No credentials or private keys in " + files.size() + " scanned files",
                "Dependencies restricted to the approved groupId allowlist",
                ssrfGuard ? "SSRF guard present (internal address checks in the URL validator)" : "SSRF guard NOT found",
                "Dangerous API patterns scanned (command execution, string-built native queries, wildcard CORS)"))).append('\n');
        r.append("## Findings\n\n");
        r.append(findings.isEmpty() ? "_No findings._\n"
                : Md.table(List.of("Severity", "Where", "Finding"),
                findings.stream().map(f -> List.of(f.severity(), f.where(), f.message())).toList()));
        r.append("\n## Residual risk\n\nNo authentication, and rate limiting is per instance; both are documented limitations.\n");
        ws.write("docs/security-review.md", r.toString());
        if (high > 0) {
            throw new IllegalStateException("security review failed with " + high + " high-severity finding(s)");
        }
        return new AgentResult("security review PASS (" + findings.size() + " non-blocking findings, " + files.size() + " files scanned)",
                "blocking findings fail the node; the agent cannot waive them");
    }
}
