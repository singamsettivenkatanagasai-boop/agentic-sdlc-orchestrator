package com.agentic.orchestrator.governance;

import com.agentic.orchestrator.util.Globs;

import java.util.List;

/**
 * Defined autonomy limits of one agent: where it may write and how many files it may change.
 * Agents can read the whole workspace but write only inside their boundary.
 */
public record AutonomyBoundary(String agent, List<String> writeGlobs, int maxFiles) {

    public boolean allows(String path) {
        return writeGlobs.stream().anyMatch(g -> Globs.matches(g, path));
    }

    public static AutonomyBoundary readOnly(String agent) {
        return new AutonomyBoundary(agent, List.of(), 0);
    }
}
