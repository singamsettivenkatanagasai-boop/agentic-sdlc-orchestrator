package com.agentic.orchestrator.governance;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Human-in-the-loop checkpoints. A worker thread blocks in {@link #await} until a human decides
 * through the REST API (or until the timeout elapses, which safe-stops the run).
 * For demos a run can register a "simulated human" responder that answers immediately; every
 * such decision is attributed to a clearly named simulated actor in the audit log.
 */
public class ApprovalService {
    public enum Kind { APPROVAL, CLARIFICATION }

    public record Request(String runId, String nodeId, Kind kind, String summary, Instant requestedAt) { }

    /** @param answers for clarifications: interpretation id -> confirmed */
    public record Decision(boolean approved, String approver, String comment, Map<String, Boolean> answers) {
        public static Decision approve(String approver, String comment) {
            return new Decision(true, approver, comment, Map.of());
        }

        public static Decision reject(String approver, String comment) {
            return new Decision(false, approver, comment, Map.of());
        }
    }

    private final Map<String, CompletableFuture<Decision>> pending = new ConcurrentHashMap<>();
    private final Map<String, Request> requests = new ConcurrentHashMap<>();
    private final Map<String, Function<Request, Decision>> responders = new ConcurrentHashMap<>();

    private static String key(String runId, String nodeId) { return runId + "/" + nodeId; }

    /** Registers a simulated human for one run (demo and test mode). */
    public void registerResponder(String runId, Function<Request, Decision> responder) {
        if (responder != null) {
            responders.put(runId, responder);
        }
    }

    /** Blocks until a decision arrives. */
    public Decision await(Request request, Duration timeout)
            throws InterruptedException, TimeoutException {
        String k = key(request.runId(), request.nodeId());
        CompletableFuture<Decision> future = new CompletableFuture<>();
        pending.put(k, future);
        requests.put(k, request);
        try {
            Function<Request, Decision> responder = responders.get(request.runId());
            if (responder != null) {
                future.complete(responder.apply(request));
            }
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw new IllegalStateException("approval failed", e.getCause());
        } finally {
            pending.remove(k);
            requests.remove(k);
        }
    }

    /** Called by the REST layer. Returns false when nothing is waiting for that node. */
    public boolean submit(String runId, String nodeId, Decision decision) {
        CompletableFuture<Decision> f = pending.get(key(runId, nodeId));
        return f != null && f.complete(decision);
    }

    public List<Request> pendingFor(String runId) {
        List<Request> out = new ArrayList<>();
        for (Request r : requests.values()) {
            if (r.runId().equals(runId)) {
                out.add(r);
            }
        }
        return out;
    }

    /** Releases every waiting worker of a run (safe-stop / operator stop). */
    public void cancelAll(String runId) {
        pending.forEach((k, f) -> {
            if (k.startsWith(runId + "/")) {
                f.completeExceptionally(new CancellationException("run stopped"));
            }
        });
    }
}
