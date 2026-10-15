package com.agentic.orchestrator.scenario;

import com.agentic.orchestrator.config.OrchestratorProperties;
import com.agentic.orchestrator.engine.Run;
import com.agentic.orchestrator.engine.RunManager;
import com.agentic.orchestrator.engine.RunRequest;
import com.agentic.orchestrator.model.MetricsSnapshot;
import com.agentic.orchestrator.model.RunOptions.Fault;
import com.agentic.orchestrator.model.RunOptions.ScheduledChange;
import com.agentic.orchestrator.model.RunStatus;
import com.agentic.orchestrator.model.Scenario;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs the demo scenarios from the command line (profile {@code scenario}) with a simulated
 * human answering the approval checkpoints, writes the evidence to the export folder
 * (docs/runs/&lt;scenario&gt;) and exits non-zero when a scenario does not end as expected.
 *
 * <pre>
 *   mvn -pl orchestrator spring-boot:run -Dspring-boot.run.profiles=scenario -Dspring-boot.run.arguments=--agentic.scenario=greenfield
 * </pre>
 */
@Component
@Profile("scenario")
public class ScenarioRunner implements CommandLineRunner {
    private record Plan(RunRequest request, RunStatus expected, String why) { }

    private static final Duration LIMIT = Duration.ofMinutes(45);

    private final RunManager runs;
    private final OrchestratorProperties props;

    public ScenarioRunner(RunManager runs, OrchestratorProperties props) {
        this.runs = runs;
        this.props = props;
    }

    @Override
    public void run(String... args) throws Exception {
        String name = props.getScenario().toLowerCase(Locale.ROOT);
        List<String> names = name.equals("all")
                ? List.of("greenfield", "brownfield", "ambiguous", "failure-build", "failure-boundary",
                "failure-secret", "rejection")
                : List.of(name);
        boolean ok = true;
        Map<String, String> summary = new LinkedHashMap<>();
        for (String n : names) {
            Plan plan = plan(n);
            System.out.println("\n=== scenario " + n + ": " + plan.why());
            Run run = runs.start(plan.request());
            if (!run.awaitClosed(LIMIT)) {
                throw new IllegalStateException("scenario " + n + " did not finish within " + LIMIT);
            }
            MetricsSnapshot m = run.metricsSnapshot();
            boolean match = run.status() == plan.expected();
            ok &= match;
            String line = String.format("%s | nodes %d/%d | retries %d | rollbacks %d | fallbacks %d | safe-stops %d | replans %d | approvals %d | %d ms | %s",
                    run.status(), m.doneNodes(), m.totalNodes(), m.retries(), m.rollbacks(), m.fallbacks(),
                    m.safeStops(), m.replans(), m.approvals(), m.endToEndMillis(),
                    match ? "as expected" : "UNEXPECTED (wanted " + plan.expected() + ")");
            summary.put(n, line);
            System.out.println(line);
        }
        System.out.println("\n=== summary");
        summary.forEach((k, v) -> System.out.println(k + ": " + v));
        if (!ok) {
            throw new IllegalStateException("at least one scenario did not end as expected");
        }
    }

    private static Plan plan(String name) {
        RunRequest base = RunRequest.of(Scenario.GREENFIELD).withLabel(name).withAutoApprove(true);
        return switch (name) {
            case "greenfield" -> new Plan(base, RunStatus.COMPLETED,
                    "agents build the URL shortener from an empty folder");
            case "brownfield" -> new Plan(RunRequest.of(Scenario.BROWNFIELD).withLabel(name).withAutoApprove(true)
                    .withChange(new ScheduledChange("implement", "design",
                            "Clients must know when to retry: document the Retry-After header on 429 responses in the API contract.")),
                    RunStatus.COMPLETED, "agents extend the existing service (expiry + rate limit) with a mid-run requirement change");
            case "ambiguous" -> new Plan(RunRequest.of(Scenario.AMBIGUOUS).withLabel(name).withAutoApprove(true)
                    .withAnswers(Map.of("malware-blocking", false, "link-expiry", true,
                            "abuse-rate-limiting", true, "access-control", false)),
                    RunStatus.COMPLETED, "vague requirement 'Make links safer': a human narrows the scope");
            case "failure-build" -> new Plan(base.withFault(Fault.BUILD_FAILURE), RunStatus.COMPLETED,
                    "developer produces code that does not compile: retries, rollbacks, then fallback agent recovers");
            case "failure-boundary" -> new Plan(base.withFault(Fault.OUT_OF_BOUNDS), RunStatus.COMPLETED,
                    "developer tries to write outside its autonomy boundary: denied, retried, fallback recovers");
            case "failure-secret" -> new Plan(base.withFault(Fault.SECRET_LEAK), RunStatus.STOPPED,
                    "developer writes a credential: fatal policy violation, safe-stop, workspace restored");
            case "rejection" -> new Plan(base.withRejectFirst("design"), RunStatus.COMPLETED,
                    "human rejects the design once: rollback, rework with the reviewer's comment, approval");
            default -> throw new IllegalArgumentException("unknown scenario '" + name + "'");
        };
    }
}
