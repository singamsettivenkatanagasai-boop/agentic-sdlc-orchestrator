package com.agentic.orchestrator.graph;

import com.agentic.orchestrator.context.RunContext;
import com.agentic.orchestrator.workspace.Workspace;

/** Everything a gate may inspect: shared run context, the workspace, the node and the graph. */
public record GateContext(RunContext context, Workspace workspace, TaskNode node, WorkflowGraph graph) { }
