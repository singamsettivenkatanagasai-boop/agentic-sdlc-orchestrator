package com.agentic.orchestrator.scenario;

import com.agentic.orchestrator.audit.EventLog;
import com.agentic.orchestrator.audit.EventType;
import com.agentic.orchestrator.engine.Run;
import com.agentic.orchestrator.engine.RunManager;
import com.agentic.orchestrator.engine.RunRequest;
import com.agentic.orchestrator.graph.NodeState;
import com.agentic.orchestrator.model.MetricsSnapshot;
import com.agentic.orchestrator.model.RunOptions.Fault;
import com.agentic.orchestrator.model.RunOptions.ScheduledChange;
import com.agentic.orchestrator.model.RunStatus;
import com.agentic.orchestrator.model.Scenario;
import com.agentic.orchestrator.support.Harness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three assignment scenarios plus the failure demos, run end to end with the real agents,
 * planner, policy and engine. Only the Maven build/test step is replaced by a fake so the suite
 * runs offline in seconds.
 */
class ScenarioFlowTest {
    @TempDir
    Path tmp;

    Harness h;
    RunManager manager;

    @BeforeEach
    void setUp() {
        h = new Harness(tmp);
        manager = h.manager();
    }

    private Run run(RunRequest request) throws Exception {
        Run run = manager.start(request.withAutoApprove(true).withLabel(request.scenario().name().toLowerCase()));
        assertTrue(run.awaitClosed(Duration.ofMinutes(2)), "scenario did not finish");
        return run;
    }

    private static NodeState state(Run run, String id) { return run.graph().node(id).state(); }

    private static long events(Run run, EventType type) {
        return run.log().events().stream().filter(e -> e.type() == type).count();
    }

    @Test
    void greenfieldBuildsTheShortenerThroughEveryStage() throws Exception {
        Run run = run(RunRequest.of(Scenario.GREENFIELD));

        assertEquals(RunStatus.COMPLETED, run.status(), String.valueOf(run.stopReason()));
        for (String id : List.of("requirements", "design", "test-plan", "implement", "tests", "security", "docs", "release", "signoff")) {
            assertEquals(NodeState.DONE, state(run, id), id);
        }
        assertTrue(run.workspace().exists("src/main/java/com/agentic/shortener/api/LinkController.java"));
        assertTrue(run.workspace().exists("src/test/java/com/agentic/shortener/api/LinkApiTest.java"));
        assertTrue(run.workspace().exists("openapi.yaml"));
        assertTrue(run.workspace().exists("docs/security-review.md"));
        assertTrue(run.workspace().exists("docs/engineering-summary.md"));
        assertTrue(run.workspace().read("docs/security-review.md").contains("Verdict: PASS"));
        assertTrue(run.workspace().read("README.md").contains("/api/v1/links"));

        MetricsSnapshot m = run.metricsSnapshot();
        assertEquals(9, m.doneNodes());
        assertEquals(1.0, m.firstAttemptSuccessRate());
        assertEquals(0, m.retries());
        assertTrue(m.approvals() >= 3, "design, release and signoff need a human: " + m.approvals());
        assertTrue(EventLog.verify(run.log().events()));
        assertTrue(Files.exists(h.exports.resolve("greenfield/report.md")));
        assertTrue(Files.exists(h.exports.resolve("greenfield/events.jsonl")));
        assertTrue(run.context().decisions().stream().anyMatch(d -> d.nodeId().equals("design") && d.decision().equals("approved")));
    }

    @Test
    void brownfieldExtendsTheExistingServiceAndAddsAMigrationNodeAtRunTime() throws Exception {
        Run run = run(RunRequest.of(Scenario.BROWNFIELD).withChange(new ScheduledChange("implement", "design",
                "Document the Retry-After header on 429 responses.")));

        assertEquals(RunStatus.COMPLETED, run.status(), String.valueOf(run.stopReason()));
        assertTrue(run.graph().has("migration"), "impact analysis found a schema change, so the plan must grow");
        assertTrue(run.graph().node("implement").dependsOn().contains("migration"));
        assertEquals(NodeState.DONE, state(run, "migration"));
        assertTrue(events(run, EventType.PLAN_EXPANDED) >= 1);

        assertTrue(run.workspace().exists("src/main/java/com/agentic/shortener/ratelimit/RateLimitFilter.java"));
        assertTrue(run.workspace().exists("src/main/resources/db/migration/V2__add_link_expiry.sql"));
        assertTrue(run.workspace().exists("src/test/java/com/agentic/shortener/api/LinkApiTest.java"),
                "baseline tests are kept");
        assertTrue(run.workspace().read("src/main/java/com/agentic/shortener/model/Link.java").contains("expiresAt"));
        assertTrue(run.workspace().read("docs/design.md").contains("Retry-After"), "the amendment reached the design");
        assertTrue(run.workspace().read("docs/CHANGELOG.md").contains("0.2.0"));
        assertTrue(run.workspace().read("README.md").contains("Per-IP rate limiting"),
                "docs are derived from the code that now exists");
        assertEquals(1, run.metricsSnapshot().replans());
        assertTrue(run.context().amendments().size() == 1);
        assertTrue(EventLog.verify(run.log().events()));
    }

    @Test
    void ambiguousRequirementIsNarrowedByAHumanBeforeAnythingIsPlanned() throws Exception {
        Run run = run(RunRequest.of(Scenario.AMBIGUOUS).withAnswers(Map.of(
                "malware-blocking", false, "link-expiry", true, "abuse-rate-limiting", true, "access-control", false)));

        assertEquals(RunStatus.COMPLETED, run.status(), String.valueOf(run.stopReason()));
        assertTrue(run.graph().has("plan-link-expiry"));
        assertTrue(run.graph().has("plan-abuse-rate-limiting"));
        assertFalse(run.graph().has("plan-malware-blocking"));
        assertFalse(run.graph().has("plan-access-control"));
        assertTrue(run.graph().node("scoped-plan").dependsOn().contains("plan-link-expiry"));
        String plan = run.workspace().read("docs/scoped-plan.md");
        assertTrue(plan.contains("Expire links automatically"));
        assertTrue(plan.contains("Block malicious destinations - not confirmed by a human"), plan);
        assertTrue(run.context().assumptions().stream().anyMatch(a -> a.contains("Scope confirmed by")));
        assertFalse(run.workspace().exists("src/main/java/com/agentic/shortener/UrlShortenerApplication.java"),
                "an ambiguous requirement must not produce code before the scope is agreed");
    }

    @Test
    void brokenCodeIsRetriedRolledBackAndFinallyRecoveredByTheFallbackAgent() throws Exception {
        Run run = run(RunRequest.of(Scenario.GREENFIELD).withFault(Fault.BUILD_FAILURE));

        assertEquals(RunStatus.COMPLETED, run.status(), String.valueOf(run.stopReason()));
        MetricsSnapshot m = run.metricsSnapshot();
        assertEquals(1, m.fallbacks());
        assertEquals(2, m.retries());
        assertTrue(m.rollbacks() >= 3);
        assertTrue(m.avgMttrMillis() > 0);
        assertTrue(m.firstAttemptSuccessRate() < 1.0);
        assertFalse(run.workspace().exists("src/main/java/com/agentic/shortener/GeneratedDefect.java"));
        assertEquals(NodeState.DONE, state(run, "signoff"));
        assertEquals(1, events(run, EventType.FALLBACK));
    }

    @Test
    void writingOutsideTheAutonomyBoundaryIsDeniedAndRecovered() throws Exception {
        Run run = run(RunRequest.of(Scenario.GREENFIELD).withFault(Fault.OUT_OF_BOUNDS));
        assertEquals(RunStatus.COMPLETED, run.status(), String.valueOf(run.stopReason()));
        assertEquals(1, run.metricsSnapshot().fallbacks());
        assertFalse(run.workspace().exists(".github/workflows/deploy.yml"));
        assertTrue(run.log().events().stream().anyMatch(e -> e.type() == EventType.AGENT && e.detail().contains("autonomy-boundary")
                || e.type() == EventType.POLICY && e.detail().contains("autonomy-boundary")));
    }

    @Test
    void aLeakedCredentialSafeStopsTheRunAndRestoresTheWorkspace() throws Exception {
        Run run = run(RunRequest.of(Scenario.GREENFIELD).withFault(Fault.SECRET_LEAK));

        assertEquals(RunStatus.STOPPED, run.status());
        assertTrue(run.stopReason().contains("no-secrets"), run.stopReason());
        assertEquals(NodeState.STOPPED, state(run, "implement"));
        assertEquals(NodeState.STOPPED, state(run, "signoff"));
        assertEquals(NodeState.DONE, state(run, "design"), "approved design work is preserved");
        assertEquals(1, run.metricsSnapshot().safeStops());
        assertFalse(run.workspace().exists("src/main/resources/application-local.properties"));
        try (Stream<Path> files = Files.walk(h.workspace)) {
            for (Path f : files.filter(Files::isRegularFile).filter(p -> !p.toString().contains(".git")).toList()) {
                assertFalse(Files.readString(f).contains("AKIA" + "IOSFODNN7EXAMPLE"), f.toString());
            }
        }
        assertEquals(1, events(run, EventType.SAFE_STOP));
        assertTrue(events(run, EventType.ROLLBACK) >= 1);
    }

    @Test
    void humanRejectionSendsTheDesignBackForRework() throws Exception {
        Run run = run(RunRequest.of(Scenario.GREENFIELD).withRejectFirst("design"));
        assertEquals(RunStatus.COMPLETED, run.status(), String.valueOf(run.stopReason()));
        assertEquals(2, run.graph().node("design").attempts());
        assertTrue(run.log().events().stream().anyMatch(e -> e.type() == EventType.APPROVAL_DECIDED && e.detail().contains("REJECTED")));
        assertEquals(1, run.metricsSnapshot().retries());
    }

    @Test
    void onlyOneRunMayUseTheWorkingFolderAtATime() throws Exception {
        Run first = manager.start(RunRequest.of(Scenario.GREENFIELD).withAutoApprove(true).withLabel("first"));
        boolean refused = false;
        try {
            manager.start(RunRequest.of(Scenario.GREENFIELD).withAutoApprove(true).withLabel("second"));
        } catch (IllegalStateException e) {
            refused = true;
        }
        assertTrue(first.awaitClosed(Duration.ofMinutes(2)));
        assertTrue(refused);
    }
}
