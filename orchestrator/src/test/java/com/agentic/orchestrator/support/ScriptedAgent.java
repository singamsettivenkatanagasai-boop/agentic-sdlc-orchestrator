package com.agentic.orchestrator.support;

import com.agentic.orchestrator.agents.Agent;
import com.agentic.orchestrator.agents.AgentResult;
import com.agentic.orchestrator.agents.AgentTask;

/** An agent whose behaviour is a lambda, for engine tests. */
public final class ScriptedAgent implements Agent {
    @FunctionalInterface
    public interface Body {
        void run(AgentTask task) throws Exception;
    }

    private final String name;
    private final Body body;

    public ScriptedAgent(String name, Body body) {
        this.name = name;
        this.body = body;
    }

    @Override
    public String name() { return name; }

    @Override
    public AgentResult execute(AgentTask task) throws Exception {
        body.run(task);
        return new AgentResult(name + " did " + task.node().id(), "scripted");
    }
}
