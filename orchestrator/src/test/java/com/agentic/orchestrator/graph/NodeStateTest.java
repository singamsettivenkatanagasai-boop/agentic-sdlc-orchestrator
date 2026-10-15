package com.agentic.orchestrator.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeStateTest {

    @Test
    void happyPathIsLegal() {
        assertTrue(NodeState.PENDING.canMoveTo(NodeState.READY));
        assertTrue(NodeState.READY.canMoveTo(NodeState.RUNNING));
        assertTrue(NodeState.RUNNING.canMoveTo(NodeState.AWAITING_APPROVAL));
        assertTrue(NodeState.AWAITING_APPROVAL.canMoveTo(NodeState.DONE));
        assertTrue(NodeState.RUNNING.canMoveTo(NodeState.DONE));
    }

    @Test
    void failurePathGoesThroughRollbackBeforeRetry() {
        assertTrue(NodeState.RUNNING.canMoveTo(NodeState.FAILED));
        assertTrue(NodeState.FAILED.canMoveTo(NodeState.ROLLED_BACK));
        assertTrue(NodeState.ROLLED_BACK.canMoveTo(NodeState.RETRYING));
        assertTrue(NodeState.RETRYING.canMoveTo(NodeState.RUNNING));
        assertFalse(NodeState.FAILED.canMoveTo(NodeState.RETRYING));
        assertFalse(NodeState.FAILED.canMoveTo(NodeState.DONE));
    }

    @Test
    void rejectionRollsBackDirectly() {
        assertTrue(NodeState.AWAITING_APPROVAL.canMoveTo(NodeState.ROLLED_BACK));
    }

    @Test
    void replanPathMakesDoneNodesStale() {
        assertTrue(NodeState.DONE.canMoveTo(NodeState.STALE));
        assertTrue(NodeState.STALE.canMoveTo(NodeState.PENDING));
        assertFalse(NodeState.DONE.canMoveTo(NodeState.RUNNING));
    }

    @Test
    void stoppedIsTerminalAndDoneCannotBeStopped() {
        for (NodeState s : NodeState.values()) {
            assertFalse(NodeState.STOPPED.canMoveTo(s), "STOPPED -> " + s);
        }
        assertFalse(NodeState.DONE.canMoveTo(NodeState.STOPPED));
        assertTrue(NodeState.RUNNING.canMoveTo(NodeState.STOPPED));
        assertTrue(NodeState.PENDING.canMoveTo(NodeState.STOPPED));
    }
}
