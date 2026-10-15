package com.agentic.orchestrator.agents;

/**
 * A worker that performs one kind of SDLC task. Agents read the whole workspace but write only
 * through the {@link com.agentic.orchestrator.workspace.GuardedWorkspace} they are given, which
 * enforces their autonomy boundary. They never decide whether their own output is accepted:
 * gates, policy and humans do.
 */
public interface Agent {
    String name();

    AgentResult execute(AgentTask task) throws Exception;
}
