package com.agentic.orchestrator.governance;

/**
 * Verdict of the policy engine.
 *
 * @param fatal for DENY: true stops the whole run (secrets, path escape); false only fails the attempt
 */
public record PolicyDecision(PolicyOutcome outcome, String rule, String reason, boolean fatal) {
    public static PolicyDecision allow() { return new PolicyDecision(PolicyOutcome.ALLOW, "none", "ok", false); }

    public static PolicyDecision requireApproval(String rule, String reason) {
        return new PolicyDecision(PolicyOutcome.REQUIRE_APPROVAL, rule, reason, false);
    }

    public static PolicyDecision deny(String rule, String reason, boolean fatal) {
        return new PolicyDecision(PolicyOutcome.DENY, rule, reason, fatal);
    }
}
