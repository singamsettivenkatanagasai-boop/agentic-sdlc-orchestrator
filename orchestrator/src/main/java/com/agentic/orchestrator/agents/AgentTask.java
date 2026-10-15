package com.agentic.orchestrator.agents;

import com.agentic.orchestrator.context.RunContext;
import com.agentic.orchestrator.graph.TaskNode;
import com.agentic.orchestrator.model.RunInfo;
import com.agentic.orchestrator.model.RunOptions;
import com.agentic.orchestrator.workspace.Workspace;

import java.util.function.Supplier;

/**
 * Everything an agent receives for one attempt.
 *
 * @param feedback why the previous attempt failed (compiler errors, rejection comment ...); null on the first attempt
 * @param attempt  1-based attempt number of the node
 */
public record AgentTask(TaskNode node, RunContext context, Workspace workspace, RunOptions options,
                        String feedback, int attempt, Supplier<RunInfo> runInfo) { }
