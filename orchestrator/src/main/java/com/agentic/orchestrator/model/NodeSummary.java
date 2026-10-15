package com.agentic.orchestrator.model;

import com.agentic.orchestrator.graph.NodeState;
import com.agentic.orchestrator.graph.Risk;

import java.util.List;

/** Read-only view of a graph node, safe to hand to agents and the REST layer. */
public record NodeSummary(String id, String agent, NodeState state, Risk risk,
                          List<String> dependsOn, int attempts, String description) { }
