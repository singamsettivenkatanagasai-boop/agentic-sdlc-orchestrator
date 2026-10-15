package com.agentic.orchestrator.workspace;

/** Result of running the project's tests. */
public record TestResult(boolean success, int tests, int failures, int errors, String output) {
    public String summary() {
        return tests + " tests, " + failures + " failures, " + errors + " errors";
    }
}
