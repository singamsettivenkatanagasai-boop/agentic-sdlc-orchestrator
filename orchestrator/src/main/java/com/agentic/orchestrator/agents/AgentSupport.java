package com.agentic.orchestrator.agents;

import com.agentic.orchestrator.workspace.Workspace;

/** Shared helpers for agents. */
final class AgentSupport {
    private AgentSupport() { }

    static String readOrEmpty(Workspace ws, String path) {
        return ws.exists(path) ? ws.read(path) : "";
    }

    static String shortHash(String sha256) {
        return sha256.length() > 12 ? sha256.substring(0, 12) : sha256;
    }
}
