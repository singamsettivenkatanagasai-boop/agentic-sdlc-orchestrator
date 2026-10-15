package com.agentic.orchestrator.agents;

import com.agentic.orchestrator.llm.LlmClient;
import com.agentic.orchestrator.llm.LlmCodeGenerator;
import com.agentic.orchestrator.model.Scenario;
import com.agentic.orchestrator.workspace.TestResult;
import com.agentic.orchestrator.workspace.Workspace;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Test engineering. Mode {@code plan} writes a risk-based test plan (runs in parallel with the
 * design). Mode {@code write-and-run} writes the tests, runs them and records the result in the
 * run context for the exit gate.
 */
public class TestAgent implements Agent {
    private static final String SYSTEM = """
            You are a senior Java test engineer in an automated delivery team.
            Reply with ONLY a JSON array of objects {"path": "...", "content": "..."} - no prose, no markdown fences.
            Write JUnit 5 + AssertJ tests (Spring Boot 3.3.5 test starter) under 'src/test/java/' only.
            Cover the acceptance criteria and failure cases. Tests must be deterministic and must not call the network.
            """;

    private final LlmClient llm;
    private final LlmCodeGenerator generator;
    private final GoldenBundle golden;

    public TestAgent(LlmClient llm, GoldenBundle golden) {
        this.llm = llm;
        this.generator = new LlmCodeGenerator(llm);
        this.golden = golden;
    }

    @Override
    public String name() { return "test"; }

    @Override
    public AgentResult execute(AgentTask t) throws IOException, InterruptedException {
        return "plan".equals(t.node().param("mode", "write-and-run")) ? plan(t) : writeAndRun(t);
    }

    private AgentResult plan(AgentTask t) {
        String low = t.context().effectiveRequirement().toLowerCase(Locale.ROOT);
        boolean brownfield = t.context().scenario() == Scenario.BROWNFIELD;
        StringBuilder p = new StringBuilder("# Test plan\n\n");
        p.append("Derived from the requirement: ").append(t.context().requirement()).append("\n\n");
        p.append("## Strategy\n\n");
        p.append(com.agentic.orchestrator.util.Md.table(List.of("Level", "Scope", "Tooling"), List.of(
                List.of("Unit", "Code generator, URL validator, service rules", "JUnit 5, AssertJ, Mockito"),
                List.of("Integration", "HTTP API end to end with the real Spring context and H2", "MockMvc"),
                List.of("Failure and security", "SSRF targets, alias conflicts, malformed input, unknown codes", "JUnit 5"),
                List.of("Regression", brownfield ? "All pre-existing tests must keep passing" : "Baseline for future changes", "mvn test"))));
        p.append("\n## Risk-based cases\n\n");
        java.util.ArrayList<String> cases = new java.util.ArrayList<>(List.of(
                "Create returns 201 and a 7-character code; creating the same URL again returns the same code",
                "Custom alias: accepted when free, 409 when taken by another URL, rejected when reserved or malformed",
                "Redirect returns 302 with Location and records exactly one click (referrer, user agent)",
                "Stats: total clicks, per-day buckets, top referrers",
                "Unknown or deleted code returns 404",
                "SSRF: localhost, 127.0.0.1, ::1, 10.x, 192.168.x, 169.254.169.254 and credential URLs are rejected"));
        if (low.contains("expir") || low.contains("ttl")) {
            cases.add("Expired link: resolve and details answer 410; no click is recorded; stats remain readable");
            cases.add("Expiry in the past is rejected at creation");
        }
        if (low.contains("rate limit") || low.contains("rate-limit") || low.contains("throttl")) {
            cases.add("Token bucket refills over time and never exceeds its capacity");
            cases.add("The request after the limit gets 429 with a Retry-After header");
        }
        p.append(com.agentic.orchestrator.util.Md.bullets(cases));
        p.append("\n## Exit criteria\n\nAll tests green; no test depends on external network access.\n");
        t.workspace().write("docs/test-plan.md", p.toString());
        return new AgentResult("test plan with " + cases.size() + " risk-based cases",
                "tests are planned from the requirement in parallel with the design, so they are not biased by the implementation");
    }

    private AgentResult writeAndRun(AgentTask t) throws IOException, InterruptedException {
        Workspace ws = t.workspace();
        boolean brownfield = t.context().scenario() == Scenario.BROWNFIELD;
        String how;
        if (llm.live()) {
            StringBuilder u = new StringBuilder();
            u.append("Requirement: ").append(t.context().effectiveRequirement()).append("\n\nTest plan:\n")
                    .append(AgentSupport.readOrEmpty(ws, "docs/test-plan.md")).append("\n\nSources under test:\n");
            for (String f : ws.list("src/main/java/**")) {
                u.append("--- ").append(f).append('\n').append(ws.read(f)).append('\n');
            }
            if (t.feedback() != null) {
                u.append("\nYour previous tests failed with:\n").append(t.feedback()).append('\n');
            }
            List<String> written = generator.generate(SYSTEM, u.toString(), ws);
            how = "generated " + written.size() + " test files with the language model";
        } else {
            Map<String, String> tests = golden.testFiles();
            tests.forEach(ws::write);
            int extra = 0;
            if (brownfield) {
                for (Map.Entry<String, String> e : PatchBundle.load(GoldenDeveloperAgent.PATCH).entrySet()) {
                    if (e.getKey().startsWith("src/test/")) {
                        ws.write(e.getKey(), e.getValue());
                        extra++;
                    }
                }
            }
            how = "replayed " + (tests.size() + extra) + " verified test files; no language model configured";
        }
        TestResult result = ws.test();
        t.context().putAttribute("testResult", result);
        ws.write("docs/test-report.md", "# Test report\n\n- Result: **" + (result.success() ? "PASS" : "FAIL")
                + "**\n- " + result.summary() + "\n- Tests written: " + how + "\n");
        return new AgentResult("tests " + (result.success() ? "passed" : "FAILED") + " (" + result.summary() + "); " + how,
                "the result is recorded in the run context; the exit gate, not the agent, decides acceptance");
    }
}
