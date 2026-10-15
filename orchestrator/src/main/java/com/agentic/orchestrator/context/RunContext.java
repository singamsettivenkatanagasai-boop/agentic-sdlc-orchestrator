package com.agentic.orchestrator.context;

import com.agentic.orchestrator.model.Scenario;
import com.agentic.orchestrator.util.Hashing;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cross-stage memory of a run: the requirement (and later amendments), assumptions, versioned
 * artifacts and the decision lineage. Shared by all agents; thread-safe because nodes run in parallel.
 */
public final class RunContext {
    private final String runId;
    private final Scenario scenario;
    private final String requirement;
    private final List<String> amendments = new CopyOnWriteArrayList<>();
    private final List<String> assumptions = new CopyOnWriteArrayList<>();
    private final Map<String, ArtifactRef> artifacts = new ConcurrentHashMap<>();
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final List<DecisionRecord> decisions = new CopyOnWriteArrayList<>();
    private final AtomicInteger decisionSeq = new AtomicInteger();

    public RunContext(String runId, Scenario scenario, String requirement) {
        this.runId = runId;
        this.scenario = scenario;
        this.requirement = requirement;
    }

    public String runId() { return runId; }
    public Scenario scenario() { return scenario; }
    /** The original requirement as submitted. */
    public String requirement() { return requirement; }
    public List<String> amendments() { return List.copyOf(amendments); }
    /** The requirement plus every amendment received so far. */
    public String effectiveRequirement() {
        return amendments.isEmpty() ? requirement
                : requirement + " | Amendments: " + String.join("; ", amendments);
    }
    public void amend(String amendment) { amendments.add(amendment); }

    public List<String> assumptions() { return List.copyOf(assumptions); }
    public void addAssumption(String a) {
        if (!assumptions.contains(a)) {
            assumptions.add(a);
        }
    }

    public ArtifactRef artifact(String path) { return artifacts.get(path); }
    public Map<String, ArtifactRef> artifacts() { return new TreeMap<>(artifacts); }

    /** Result of registering an artifact version. {@code changed} is true when an earlier version had a different hash. */
    public record Registration(ArtifactRef ref, boolean changed) { }

    /** Registers (or re-registers) an artifact; bumps the version only when the content changed. */
    public synchronized Registration registerArtifact(String path, String content, String producerNode) {
        String hash = Hashing.sha256(content);
        ArtifactRef previous = artifacts.get(path);
        if (previous != null && previous.sha256().equals(hash)) {
            return new Registration(previous, false);
        }
        int version = previous == null ? 1 : previous.version() + 1;
        ArtifactRef ref = new ArtifactRef(path, hash, version, producerNode);
        artifacts.put(path, ref);
        return new Registration(ref, previous != null);
    }

    public void putAttribute(String key, Object value) {
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }
    public Object attribute(String key) { return attributes.get(key); }

    public DecisionRecord recordDecision(String nodeId, String actor, String decision, String rationale,
                                         Map<String, String> inputs, Map<String, String> outputs) {
        DecisionRecord d = new DecisionRecord("D-" + decisionSeq.incrementAndGet(), nodeId, actor, decision,
                rationale, Map.copyOf(inputs), Map.copyOf(outputs), Instant.now());
        decisions.add(d);
        return d;
    }
    public List<DecisionRecord> decisions() { return List.copyOf(decisions); }
}
