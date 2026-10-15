package com.agentic.orchestrator.audit;

/** Kinds of audit events. Every state change, gate, policy verdict and human action is recorded. */
public enum EventType {
    RUN_STARTED, PLAN_CREATED, PLAN_EXPANDED, STATE_CHANGE, GATE, AGENT, POLICY,
    APPROVAL_REQUESTED, APPROVAL_DECIDED, RETRY, FALLBACK, ROLLBACK, REPLAN,
    SAFE_STOP, CHECKPOINT, DECISION, RUN_FINISHED
}
