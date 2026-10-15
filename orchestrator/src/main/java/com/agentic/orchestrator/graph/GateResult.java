package com.agentic.orchestrator.graph;

/** Outcome of evaluating an entry or exit gate. */
public record GateResult(boolean passed, String reason) {
    public static GateResult pass() { return new GateResult(true, "ok"); }
    public static GateResult fail(String reason) { return new GateResult(false, reason); }
}
