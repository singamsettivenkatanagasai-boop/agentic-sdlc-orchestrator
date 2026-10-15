package com.agentic.orchestrator.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;

/**
 * Tunable behaviour of one run. Immutable; use the {@code with...} methods to derive variants.
 *
 * @param fault             optional fault injected into the Developer agent (failure demos)
 * @param maxReplans        upper bound on re-plans per run (bounded autonomy)
 * @param approvalTimeout   how long a human decision may stay pending before the run is safe-stopped
 * @param backoffBaseMillis base delay for exponential retry backoff
 * @param change            optional scripted requirement change (demonstrates dynamic re-planning)
 */
public record RunOptions(Fault fault, int maxReplans, Duration approvalTimeout,
                         long backoffBaseMillis, ScheduledChange change) {

    /** Faults the Developer agent can be told to inject. */
    public enum Fault {
        NONE,
        /** Generated code does not compile: exercises retry, fallback and rollback. */
        BUILD_FAILURE,
        /** Generated output contains a credential: exercises policy DENY and safe-stop. */
        SECRET_LEAK,
        /** Agent tries to write outside its autonomy boundary: exercises boundary enforcement. */
        OUT_OF_BOUNDS
    }

    /**
     * A requirement change. Applied once {@code afterNode} has completed, or at the next
     * scheduling tick when {@code afterNode} is null (manual re-plan through the API).
     */
    @Schema(description = "A requirement change: the target node and everything downstream of it are re-run")
    public record ScheduledChange(
            @Schema(description = "Apply once this node is done; empty = at the next scheduling tick", example = "implement")
            String afterNode,
            @Schema(description = "First node whose work is out of date", example = "design")
            String targetNode,
            @Schema(description = "Text appended to the requirement",
                    example = "Clients must know when to retry: document the Retry-After header on 429 responses in the API contract.")
            String amendment) { }

    public static RunOptions defaults() {
        return new RunOptions(Fault.NONE, 2, Duration.ofMinutes(30), 200, null);
    }

    public RunOptions withFault(Fault f) { return new RunOptions(f, maxReplans, approvalTimeout, backoffBaseMillis, change); }
    public RunOptions withChange(ScheduledChange c) { return new RunOptions(fault, maxReplans, approvalTimeout, backoffBaseMillis, c); }
    public RunOptions withApprovalTimeout(Duration d) { return new RunOptions(fault, maxReplans, d, backoffBaseMillis, change); }
    public RunOptions withBackoffBaseMillis(long ms) { return new RunOptions(fault, maxReplans, approvalTimeout, ms, change); }
    public RunOptions withMaxReplans(int n) { return new RunOptions(fault, n, approvalTimeout, backoffBaseMillis, change); }
}
