package com.agentic.orchestrator.graph;

import java.util.function.Function;

/**
 * A named check that must pass before a node starts (entry gate) or before its output is
 * accepted (exit gate). Gates never throw: an exception inside a check counts as a failed gate.
 */
public record Gate(String name, Function<GateContext, GateResult> check) {

    public static Gate none() {
        return new Gate("none", c -> GateResult.pass());
    }

    public GateResult evaluate(GateContext ctx) {
        try {
            return check.apply(ctx);
        } catch (RuntimeException e) {
            return GateResult.fail(name + " could not be evaluated: " + e);
        }
    }
}
