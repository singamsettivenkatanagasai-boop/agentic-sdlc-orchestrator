package com.agentic.orchestrator.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowGraphTest {

    private static TaskNode node(String id, String... deps) {
        return TaskNode.builder(id, "agent").dependsOn(deps).build();
    }

    private static WorkflowGraph diamond() {
        WorkflowGraph g = new WorkflowGraph();
        g.add(node("a"));
        g.add(node("b", "a"));
        g.add(node("c", "a"));
        g.add(node("d", "b", "c"));
        g.validate();
        return g;
    }

    @Test
    void onlyRootsAreReadyAtTheStart() {
        assertEquals(List.of("a"), diamond().readyNodes().stream().map(TaskNode::id).toList());
    }

    @Test
    void parallelBranchesBecomeReadyTogetherAndTheJoinWaitsForBoth() {
        WorkflowGraph g = diamond();
        g.node("a").setState(NodeState.DONE);
        assertEquals(Set.of("b", "c"), Set.copyOf(g.readyNodes().stream().map(TaskNode::id).toList()));
        g.node("b").setState(NodeState.DONE);
        assertEquals(Set.of("c"), Set.copyOf(g.readyNodes().stream().map(TaskNode::id).toList()));
        g.node("c").setState(NodeState.DONE);
        assertEquals(List.of("d"), g.readyNodes().stream().map(TaskNode::id).toList());
    }

    @Test
    void downstreamIsTheBlastRadiusOfAChange() {
        assertEquals(Set.of("b", "c", "d"), diamond().downstreamOf("a"));
        assertEquals(Set.of("d"), diamond().downstreamOf("b"));
        assertTrue(diamond().downstreamOf("d").isEmpty());
    }

    @Test
    void cyclesAreRejected() {
        WorkflowGraph g = new WorkflowGraph();
        g.add(node("a", "b"));
        g.add(node("b", "a"));
        assertThrows(IllegalStateException.class, g::validate);
    }

    @Test
    void unknownDependenciesAreRejected() {
        WorkflowGraph g = new WorkflowGraph();
        g.add(node("a", "ghost"));
        assertThrows(IllegalStateException.class, g::validate);
    }

    @Test
    void duplicateNodeIdsAreRejected() {
        WorkflowGraph g = new WorkflowGraph();
        g.add(node("a"));
        assertThrows(IllegalArgumentException.class, () -> g.add(node("a")));
    }

    @Test
    void addingACyclicDependencyIsRefusedAndLeavesTheGraphValid() {
        WorkflowGraph g = diamond();
        assertThrows(IllegalStateException.class, () -> g.addDependency("a", "d"));
        g.validate();
        assertTrue(g.node("a").dependsOn().isEmpty());
    }

    @Test
    void addingAValidDependencyChangesReadiness() {
        WorkflowGraph g = diamond();
        g.addDependency("c", "b");
        g.node("a").setState(NodeState.DONE);
        assertEquals(List.of("b"), g.readyNodes().stream().map(TaskNode::id).toList());
    }

    @Test
    void allDoneOnlyWhenEveryNodeIsDone() {
        WorkflowGraph g = diamond();
        assertTrue(!g.allDone());
        g.nodes().forEach(n -> n.setState(NodeState.DONE));
        assertTrue(g.allDone());
    }
}
