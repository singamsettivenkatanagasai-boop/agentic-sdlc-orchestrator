package com.agentic.orchestrator.graph;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Execution state of a single task node. Legal transitions are encoded here so the state
 * machine rejects anything else (stateful, non-linear execution).
 *
 * <pre>
 * PENDING -> READY -> RUNNING -> DONE
 *                       |   \-> AWAITING_APPROVAL -> DONE | ROLLED_BACK
 *                       \-> FAILED -> ROLLED_BACK -> RETRYING -> RUNNING
 * DONE -> STALE -> PENDING          (re-plan: upstream output changed)
 * any non-DONE state -> STOPPED     (safe-stop, terminal)
 * </pre>
 */
public enum NodeState {
    PENDING, READY, RUNNING, AWAITING_APPROVAL, DONE, FAILED, RETRYING, ROLLED_BACK, STALE, STOPPED;

    private static final Map<NodeState, Set<NodeState>> ALLOWED = Map.ofEntries(
            Map.entry(PENDING, EnumSet.of(READY, STOPPED)),
            Map.entry(READY, EnumSet.of(RUNNING, STOPPED)),
            Map.entry(RUNNING, EnumSet.of(AWAITING_APPROVAL, DONE, FAILED, STOPPED)),
            Map.entry(AWAITING_APPROVAL, EnumSet.of(DONE, ROLLED_BACK, STOPPED)),
            Map.entry(DONE, EnumSet.of(STALE)),
            Map.entry(FAILED, EnumSet.of(ROLLED_BACK, STOPPED)),
            Map.entry(RETRYING, EnumSet.of(RUNNING, STOPPED)),
            Map.entry(ROLLED_BACK, EnumSet.of(RETRYING, STOPPED)),
            Map.entry(STALE, EnumSet.of(PENDING, STOPPED)),
            Map.entry(STOPPED, EnumSet.noneOf(NodeState.class)));

    /** True when moving from this state to {@code next} is a legal transition. */
    public boolean canMoveTo(NodeState next) {
        return ALLOWED.get(this).contains(next);
    }
}
