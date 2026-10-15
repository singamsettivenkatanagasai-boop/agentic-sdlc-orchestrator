package com.agentic.orchestrator.graph;

import com.agentic.orchestrator.model.NodeSummary;

import java.util.*;

/**
 * Explicit dependency graph of a run. A node may start only when every dependency is DONE,
 * which gives sequential paths, parallel branches and synchronisation (join) points.
 *
 * <p>All methods are synchronized: the scheduler thread reads the graph while worker threads
 * may be running nodes, and the planner can expand the graph at runtime.
 */
public final class WorkflowGraph {
    private final Map<String, TaskNode> nodes = new LinkedHashMap<>();

    public synchronized void add(TaskNode node) {
        if (nodes.putIfAbsent(node.id(), node) != null) {
            throw new IllegalArgumentException("duplicate node id: " + node.id());
        }
    }

    public synchronized boolean has(String id) { return nodes.containsKey(id); }

    public synchronized TaskNode node(String id) {
        TaskNode n = nodes.get(id);
        if (n == null) {
            throw new NoSuchElementException("unknown node: " + id);
        }
        return n;
    }

    /** Nodes in insertion order. */
    public synchronized List<TaskNode> nodes() { return new ArrayList<>(nodes.values()); }

    /** Adds {@code dependsOn} as a prerequisite of {@code nodeId}; rejects unknown nodes and cycles. */
    public synchronized void addDependency(String nodeId, String dependsOn) {
        TaskNode n = node(nodeId);
        node(dependsOn);
        boolean added = n.addDependency(dependsOn);
        try {
            validate();
        } catch (IllegalStateException e) {
            if (added) {
                n.removeDependency(dependsOn);   // keep the graph valid
            }
            throw e;
        }
    }

    /** Fails on unknown dependencies or cycles (Kahn's algorithm). */
    public synchronized void validate() {
        Map<String, Integer> indegree = new HashMap<>();
        for (TaskNode n : nodes.values()) {
            indegree.put(n.id(), 0);
        }
        for (TaskNode n : nodes.values()) {
            for (String d : n.dependsOn()) {
                if (!nodes.containsKey(d)) {
                    throw new IllegalStateException("node '" + n.id() + "' depends on unknown node '" + d + "'");
                }
                indegree.merge(n.id(), 1, Integer::sum);
            }
        }
        Deque<String> queue = new ArrayDeque<>();
        indegree.forEach((id, deg) -> { if (deg == 0) queue.add(id); });
        int visited = 0;
        while (!queue.isEmpty()) {
            String id = queue.poll();
            visited++;
            for (TaskNode n : nodes.values()) {
                if (n.dependsOn().contains(id) && indegree.merge(n.id(), -1, Integer::sum) == 0) {
                    queue.add(n.id());
                }
            }
        }
        if (visited != nodes.size()) {
            throw new IllegalStateException("workflow graph contains a cycle");
        }
    }

    /** PENDING nodes whose dependencies are all DONE: they can be launched now (in parallel). */
    public synchronized List<TaskNode> readyNodes() {
        List<TaskNode> ready = new ArrayList<>();
        for (TaskNode n : nodes.values()) {
            if (n.state() == NodeState.PENDING
                    && n.dependsOn().stream().allMatch(d -> nodes.get(d).state() == NodeState.DONE)) {
                ready.add(n);
            }
        }
        return ready;
    }

    /** All nodes that transitively depend on {@code id} (the blast radius of a change). */
    public synchronized Set<String> downstreamOf(String id) {
        Set<String> result = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(List.of(id));
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (TaskNode n : nodes.values()) {
                if (n.dependsOn().contains(current) && result.add(n.id())) {
                    queue.add(n.id());
                }
            }
        }
        return result;
    }

    public synchronized boolean allDone() {
        return nodes.values().stream().allMatch(n -> n.state() == NodeState.DONE);
    }

    public synchronized List<NodeSummary> summaries() {
        List<NodeSummary> out = new ArrayList<>();
        for (TaskNode n : nodes.values()) {
            out.add(new NodeSummary(n.id(), n.agent(), n.state(), n.risk(), n.dependsOn(),
                    n.attempts(), n.description()));
        }
        return out;
    }
}
