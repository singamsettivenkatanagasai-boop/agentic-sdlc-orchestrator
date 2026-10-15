package com.agentic.orchestrator.graph;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One unit of work in the workflow graph: which agent does it, what it depends on, how risky
 * it is, which gates guard it and how often it may be retried.
 *
 * <p>The mutable parts (state, attempts, dependencies) are thread-safe because nodes run on
 * parallel worker threads. Only {@code NodeStateMachine} should call {@link #setState}.
 */
public final class TaskNode {
    private final String id;
    private final String agent;
    private final String fallbackAgent;
    private final String description;
    private final List<String> dependsOn = new CopyOnWriteArrayList<>();
    private final Risk risk;
    private final Gate entryGate;
    private final Gate exitGate;
    private final int maxAttempts;
    private final boolean humanInput;
    private final Map<String, String> params;

    private volatile NodeState state = NodeState.PENDING;
    private volatile Instant firstFailureAt;
    private final AtomicInteger attempts = new AtomicInteger();

    private TaskNode(Builder b) {
        this.id = b.id;
        this.agent = b.agent;
        this.fallbackAgent = b.fallbackAgent;
        this.description = b.description;
        this.dependsOn.addAll(b.dependsOn);
        this.risk = b.risk;
        this.entryGate = b.entryGate;
        this.exitGate = b.exitGate;
        this.maxAttempts = b.maxAttempts;
        this.humanInput = b.humanInput;
        this.params = Map.copyOf(b.params);
    }

    public static Builder builder(String id, String agent) { return new Builder(id, agent); }

    public String id() { return id; }
    public String agent() { return agent; }
    /** Agent used after the primary agent exhausted its retries; may be null. */
    public String fallbackAgent() { return fallbackAgent; }
    public String description() { return description; }
    public List<String> dependsOn() { return List.copyOf(dependsOn); }
    public Risk risk() { return risk; }
    public Gate entryGate() { return entryGate; }
    public Gate exitGate() { return exitGate; }
    public int maxAttempts() { return maxAttempts; }
    /** True when a human must answer a question (clarification) before the node completes. */
    public boolean humanInput() { return humanInput; }
    public String param(String key, String defaultValue) { return params.getOrDefault(key, defaultValue); }

    public NodeState state() { return state; }
    public void setState(NodeState s) { this.state = s; }
    public Instant firstFailureAt() { return firstFailureAt; }
    public void setFirstFailureAt(Instant t) { this.firstFailureAt = t; }
    public int attempts() { return attempts.get(); }
    public int incrementAttempts() { return attempts.incrementAndGet(); }

    /** Adds a dependency edge (the planner expands the graph at runtime); true when it was new. */
    boolean addDependency(String nodeId) {
        if (dependsOn.contains(nodeId)) {
            return false;
        }
        dependsOn.add(nodeId);
        return true;
    }

    void removeDependency(String nodeId) {
        dependsOn.remove(nodeId);
    }

    public static final class Builder {
        private final String id;
        private final String agent;
        private String fallbackAgent;
        private String description = "";
        private final List<String> dependsOn = new ArrayList<>();
        private Risk risk = Risk.LOW;
        private Gate entryGate = Gate.none();
        private Gate exitGate = Gate.none();
        private int maxAttempts = 3;
        private boolean humanInput;
        private final Map<String, String> params = new HashMap<>();

        private Builder(String id, String agent) { this.id = id; this.agent = agent; }

        public Builder description(String d) { this.description = d; return this; }
        public Builder dependsOn(String... ids) { this.dependsOn.addAll(List.of(ids)); return this; }
        public Builder risk(Risk r) { this.risk = r; return this; }
        public Builder entryGate(Gate g) { this.entryGate = g; return this; }
        public Builder exitGate(Gate g) { this.exitGate = g; return this; }
        public Builder fallbackAgent(String a) { this.fallbackAgent = a; return this; }
        public Builder maxAttempts(int n) { this.maxAttempts = n; return this; }
        public Builder humanInput(boolean b) { this.humanInput = b; return this; }
        public Builder param(String k, String v) { this.params.put(k, v); return this; }
        public TaskNode build() { return new TaskNode(this); }
    }
}
