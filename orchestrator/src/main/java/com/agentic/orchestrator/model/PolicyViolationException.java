package com.agentic.orchestrator.model;

/**
 * Thrown when an agent action breaks a guardrail (secret in output, write outside its
 * autonomy boundary, path escape, disallowed dependency ...).
 *
 * <p>{@code fatal} violations (secrets, path escapes) stop the whole run immediately.
 * Non-fatal ones fail the current attempt so the retry / fallback / rollback path can recover.
 */
public class PolicyViolationException extends RuntimeException {
    private final String rule;
    private final boolean fatal;

    public PolicyViolationException(String rule, String reason, boolean fatal) {
        super(reason);
        this.rule = rule;
        this.fatal = fatal;
    }

    public String rule() { return rule; }
    public boolean fatal() { return fatal; }
}
