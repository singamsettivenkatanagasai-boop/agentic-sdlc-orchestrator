package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.agents.AgentResult;
import com.agentic.orchestrator.workspace.GuardedWorkspace;

import java.util.Map;
import java.util.Set;

/**
 * What a worker thread hands back to the coordinator. A worker never marks its node DONE: the
 * coordinator accepts or discards the result on a single thread, which keeps re-planning race-free.
 */
public record Completion(String nodeId, Outcome outcome, String agent, AgentResult result,
                         Set<String> changedPaths, GuardedWorkspace attemptWorkspace,
                         String approver, String approvalComment, Map<String, Boolean> answers,
                         int failedAttempts, long mttrMillis, boolean usedFallback, String reason) {

    public enum Outcome {
        /** Output produced, gates passed, approved if needed. */
        SUCCESS,
        /** A fatal policy violation or an unrecoverable condition: safe-stop the run. */
        FATAL,
        /** Retries and fallback are used up: safe-stop the run. */
        EXHAUSTED,
        /** The worker was interrupted because the run is stopping. */
        STOPPED
    }

    static Completion failure(String nodeId, Outcome outcome, String agent, String reason,
                              int failedAttempts, boolean usedFallback) {
        return new Completion(nodeId, outcome, agent, null, Set.of(), null, null, null, Map.of(),
                failedAttempts, -1, usedFallback, reason);
    }
}
