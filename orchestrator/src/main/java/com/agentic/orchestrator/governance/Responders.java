package com.agentic.orchestrator.governance;

import com.agentic.orchestrator.governance.ApprovalService.Decision;
import com.agentic.orchestrator.governance.ApprovalService.Kind;
import com.agentic.orchestrator.governance.ApprovalService.Request;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/** Simulated humans for demos and tests. Real runs answer through the REST API instead. */
public final class Responders {
    public static final String SIMULATED = "human:simulated-reviewer";

    private Responders() { }

    /** Approves every approval and answers every clarification with the given answers. */
    public static Function<Request, Decision> simulatedHuman(Map<String, Boolean> clarificationAnswers) {
        return req -> req.kind() == Kind.CLARIFICATION
                ? new Decision(true, SIMULATED, "scripted clarification answers", clarificationAnswers)
                : Decision.approve(SIMULATED, "reviewed and approved (simulated)");
    }

    /** Rejects the first request for {@code nodeId}, then delegates. */
    public static Function<Request, Decision> rejectFirst(String nodeId, Function<Request, Decision> then) {
        AtomicBoolean rejected = new AtomicBoolean();
        return req -> {
            if (req.nodeId().equals(nodeId) && rejected.compareAndSet(false, true)) {
                return Decision.reject(SIMULATED, "rejected (simulated): please document error handling explicitly");
            }
            return then.apply(req);
        };
    }
}
