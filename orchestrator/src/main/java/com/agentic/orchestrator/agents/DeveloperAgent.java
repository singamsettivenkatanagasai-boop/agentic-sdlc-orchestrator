package com.agentic.orchestrator.agents;

import com.agentic.orchestrator.llm.LlmClient;
import com.agentic.orchestrator.llm.LlmCodeGenerator;
import com.agentic.orchestrator.model.RunOptions.Fault;
import com.agentic.orchestrator.workspace.Workspace;

import java.io.IOException;
import java.util.List;

/**
 * Implementation agent. With a language model configured it generates the code (and feeds the
 * previous attempt's compiler or test errors back into the prompt); without one it replays the
 * verified implementation, so the prototype runs end to end offline.
 *
 * <p>It can be told to inject a fault ({@link Fault}) to demonstrate the failure handling:
 * retries, fallback, rollback, policy denial and safe-stop.
 */
public class DeveloperAgent implements Agent {
    private static final String SYSTEM = """
            You are a senior Java engineer in an automated delivery team.
            Reply with ONLY a JSON array of objects {"path": "...", "content": "..."} - no prose, no markdown fences.
            Constraints: Java 21, Spring Boot 3.3.5, Maven, base package com.agentic.shortener, in-memory H2.
            Write only 'pom.xml' and files under 'src/main/'. Allowed Maven groupIds: org.springframework.boot,
            com.h2database, com.github.ben-manes.caffeine. Never embed secrets. The project must pass 'mvn compile'.
            Follow the OpenAPI contract and the design exactly.
            """;

    private final LlmClient llm;
    private final LlmCodeGenerator generator;
    private final GoldenDeveloperAgent golden;

    public DeveloperAgent(LlmClient llm, GoldenDeveloperAgent golden) {
        this.llm = llm;
        this.generator = new LlmCodeGenerator(llm);
        this.golden = golden;
    }

    @Override
    public String name() { return "developer"; }

    @Override
    public AgentResult execute(AgentTask t) throws IOException, InterruptedException {
        String mode = t.node().param("mode", "greenfield");
        String how;
        if (llm.live() && !mode.equals("migration")) {
            List<String> written = generator.generate(SYSTEM, userPrompt(t, mode), t.workspace());
            how = "generated " + written.size() + " files with the language model";
        } else {
            int n = golden.apply(t.workspace(), mode);
            how = "replayed the verified implementation (" + n + " files); no language model configured";
        }
        String fault = injectFault(t, mode);
        return new AgentResult("developer (" + mode + "): " + how + fault,
                t.attempt() > 1 ? "retry with feedback from the previous failure" : "implemented from the approved design and contract");
    }

    private static String userPrompt(AgentTask t, String mode) {
        Workspace ws = t.workspace();
        StringBuilder u = new StringBuilder();
        u.append("Requirement: ").append(t.context().effectiveRequirement()).append("\n\n");
        u.append("Design:\n").append(AgentSupport.readOrEmpty(ws, "docs/design.md")).append("\n\n");
        u.append("OpenAPI contract:\n").append(AgentSupport.readOrEmpty(ws, "openapi.yaml")).append("\n\n");
        if (mode.equals("brownfield")) {
            u.append("This is an enhancement of an existing project. Return only the files that change or are new.\n");
            u.append("Existing files:\n");
            for (String f : ws.list("src/main/**")) {
                u.append("--- ").append(f).append('\n').append(ws.read(f)).append('\n');
            }
            u.append("--- pom.xml\n").append(AgentSupport.readOrEmpty(ws, "pom.xml")).append('\n');
        }
        if (t.feedback() != null) {
            u.append("\nYour previous attempt failed with the following output. Fix these problems:\n")
                    .append(t.feedback()).append('\n');
        }
        return u.toString();
    }

    private static String injectFault(AgentTask t, String mode) {
        Fault fault = t.options().fault();
        if (fault == Fault.NONE || mode.equals("migration")) {
            return "";
        }
        Workspace ws = t.workspace();
        switch (fault) {
            case BUILD_FAILURE -> ws.write("src/main/java/com/agentic/shortener/GeneratedDefect.java",
                    "package com.agentic.shortener;\n\nclass GeneratedDefect {\n    int broken = \"not an int\";\n}\n");
            case SECRET_LEAK -> ws.write("src/main/resources/application-local.properties",
                    "# local overrides\naws.secret.accessKeyId=" + "AKIA" + "IOSFODNN7EXAMPLE\n");
            case OUT_OF_BOUNDS -> ws.write(".github/workflows/deploy.yml", "name: deploy\non: push\n");
            default -> { }
        }
        return " [fault injected: " + fault + "]";
    }
}
