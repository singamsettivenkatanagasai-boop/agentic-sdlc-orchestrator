package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.model.RunOptions;
import com.agentic.orchestrator.model.RunOptions.Fault;
import com.agentic.orchestrator.model.RunOptions.ScheduledChange;
import com.agentic.orchestrator.model.Scenario;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.util.Map;

/**
 * Everything needed to start a run. Plain fields only so it can be posted as JSON.
 *
 * @param scenario              greenfield, brownfield or ambiguous
 * @param label                 name of the evidence folder (defaults to the run id)
 * @param requirement           the requirement text (a scenario default is used when blank)
 * @param autoApprove           a simulated human answers every approval immediately (demos and tests)
 * @param clarificationAnswers  answers the simulated human gives to clarification questions
 * @param rejectFirstNode       with autoApprove: the simulated human rejects this node once
 * @param fault                 fault to inject into the Developer agent
 * @param change                scripted requirement change (dynamic re-planning)
 * @param approvalTimeoutSeconds how long to wait for a human before the run safe-stops
 */
@Schema(description = "Starts a run. Only 'scenario' is required.")
public record RunRequest(
        @Schema(description = "GREENFIELD builds from an empty folder, BROWNFIELD changes the existing shortener, "
                + "AMBIGUOUS clarifies a vague requirement first", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "GREENFIELD")
        Scenario scenario,
        @Schema(description = "Name of the evidence folder data/runs/<label> (defaults to the run id)", example = "my-run")
        String label,
        @Schema(description = "The requirement text; the scenario's default requirement is used when empty",
                example = "Build a URL shortener service that creates short links, redirects visitors to the "
                        + "original URL and reports click statistics.")
        String requirement,
        @Schema(description = "true: a simulated human answers every checkpoint immediately; "
                + "false: the run waits for decisions over POST /runs/{id}/nodes/{node}/approval", example = "false")
        boolean autoApprove,
        @Schema(description = "With autoApprove: answers the simulated human gives to clarification questions "
                + "(AMBIGUOUS scenario), interpretation id -> in scope",
                example = "{\"link-expiry\": true, \"abuse-rate-limiting\": true, \"malware-blocking\": false, \"access-control\": false}")
        Map<String, Boolean> clarificationAnswers,
        @Schema(description = "With autoApprove: the simulated human rejects this node once", example = "design")
        String rejectFirstNode,
        @Schema(description = "Fault injected into the Developer agent to demonstrate failure handling", example = "NONE")
        Fault fault,
        @Schema(description = "Requirement change applied after a node completes (dynamic re-planning)")
        ScheduledChange change,
        @Schema(description = "How long a checkpoint may wait for a human before the run safe-stops (default 1800)",
                example = "1800")
        Long approvalTimeoutSeconds) {

    public static RunRequest of(Scenario scenario) {
        return new RunRequest(scenario, null, null, false, Map.of(), null, Fault.NONE, null, null);
    }

    public RunRequest withLabel(String l) {
        return new RunRequest(scenario, l, requirement, autoApprove, clarificationAnswers, rejectFirstNode, fault, change, approvalTimeoutSeconds);
    }

    public RunRequest withAutoApprove(boolean b) {
        return new RunRequest(scenario, label, requirement, b, clarificationAnswers, rejectFirstNode, fault, change, approvalTimeoutSeconds);
    }

    public RunRequest withAnswers(Map<String, Boolean> a) {
        return new RunRequest(scenario, label, requirement, autoApprove, a, rejectFirstNode, fault, change, approvalTimeoutSeconds);
    }

    public RunRequest withRejectFirst(String node) {
        return new RunRequest(scenario, label, requirement, autoApprove, clarificationAnswers, node, fault, change, approvalTimeoutSeconds);
    }

    public RunRequest withFault(Fault f) {
        return new RunRequest(scenario, label, requirement, autoApprove, clarificationAnswers, rejectFirstNode, f, change, approvalTimeoutSeconds);
    }

    public RunRequest withChange(ScheduledChange c) {
        return new RunRequest(scenario, label, requirement, autoApprove, clarificationAnswers, rejectFirstNode, fault, c, approvalTimeoutSeconds);
    }

    public RunRequest withRequirement(String r) {
        return new RunRequest(scenario, label, r, autoApprove, clarificationAnswers, rejectFirstNode, fault, change, approvalTimeoutSeconds);
    }

    public RunRequest withApprovalTimeoutSeconds(Long s) {
        return new RunRequest(scenario, label, requirement, autoApprove, clarificationAnswers, rejectFirstNode, fault, change, s);
    }

    /** The requirement to use: the request's own text or the scenario default. */
    public String effectiveRequirement() {
        if (requirement != null && !requirement.isBlank()) {
            return requirement;
        }
        return switch (scenario) {
            case GREENFIELD -> "Build a URL shortener service that creates short links, redirects visitors to the "
                    + "original URL and reports click statistics.";
            case BROWNFIELD -> "Add optional link expiry and per-IP rate limiting to the existing URL shortener "
                    + "without breaking its current API.";
            case AMBIGUOUS -> "Make links safer.";
        };
    }

    public RunOptions toOptions() {
        RunOptions o = RunOptions.defaults();
        if (fault != null) {
            o = o.withFault(fault);
        }
        if (change != null) {
            o = o.withChange(change);
        }
        if (approvalTimeoutSeconds != null) {
            o = o.withApprovalTimeout(Duration.ofSeconds(approvalTimeoutSeconds));
        }
        return o;
    }
}
