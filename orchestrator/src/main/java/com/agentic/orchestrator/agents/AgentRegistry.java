package com.agentic.orchestrator.agents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** Looks agents up by name. */
public class AgentRegistry {
    private final Map<String, Agent> byName = new HashMap<>();

    public AgentRegistry(List<Agent> agents) {
        for (Agent a : agents) {
            if (byName.putIfAbsent(a.name(), a) != null) {
                throw new IllegalArgumentException("duplicate agent name: " + a.name());
            }
        }
    }

    public Agent get(String name) {
        Agent a = byName.get(name);
        if (a == null) {
            throw new NoSuchElementException("no agent registered with name '" + name + "'");
        }
        return a;
    }
}
