package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.audit.EventLog;
import com.agentic.orchestrator.audit.EventType;
import com.agentic.orchestrator.graph.NodeState;
import com.agentic.orchestrator.graph.TaskNode;

/**
 * The only place that changes a node's state. Illegal transitions are rejected (the transition
 * table lives in {@link NodeState}) and every legal one is written to the audit log.
 */
public final class NodeStateMachine {
    private final EventLog log;

    public NodeStateMachine(EventLog log) { this.log = log; }

    public synchronized void move(TaskNode node, NodeState to, String reason) {
        NodeState from = node.state();
        if (!from.canMoveTo(to)) {
            throw new IllegalStateException("illegal transition " + from + " -> " + to + " for node " + node.id());
        }
        node.setState(to);
        log.append(node.id(), EventType.STATE_CHANGE, "orchestrator", from + " -> " + to + ": " + reason);
    }

    /** Moves to {@code to} when legal and returns whether it did (used while stopping). */
    public synchronized boolean tryMove(TaskNode node, NodeState to, String reason) {
        if (!node.state().canMoveTo(to)) {
            return false;
        }
        move(node, to, reason);
        return true;
    }
}
