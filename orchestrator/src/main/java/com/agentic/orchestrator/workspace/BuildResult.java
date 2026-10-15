package com.agentic.orchestrator.workspace;

/** Result of compiling the project in a workspace. */
public record BuildResult(boolean success, String output) { }
