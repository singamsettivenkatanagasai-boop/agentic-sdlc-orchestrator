package com.agentic.orchestrator.governance;

import java.util.Map;

/** The autonomy boundary of every agent. Unknown agents get a read-only boundary (deny by default). */
public class AutonomyBoundaries {
    private final Map<String, AutonomyBoundary> byAgent = Map.ofEntries(
            entry("requirements", 5, "docs/**"),
            entry("architect", 6, "docs/**", "openapi.yaml"),
            entry("developer", 60, "pom.xml", "src/main/**"),
            entry("golden-developer", 60, "pom.xml", "src/main/**"),
            entry("test", 60, "src/test/**", "docs/**"),
            entry("security", 3, "docs/**"),
            entry("docs", 8, "README.md", "docs/**"),
            entry("release", 3, "docs/**"),
            entry("reviewer", 3, "docs/**"),
            entry("scope-planner", 5, "docs/**"));

    private static Map.Entry<String, AutonomyBoundary> entry(String agent, int maxFiles, String... globs) {
        return Map.entry(agent, new AutonomyBoundary(agent, java.util.List.of(globs), maxFiles));
    }

    public AutonomyBoundary forAgent(String agent) {
        return byAgent.getOrDefault(agent, AutonomyBoundary.readOnly(agent));
    }
}
