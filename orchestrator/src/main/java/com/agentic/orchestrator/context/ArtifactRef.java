package com.agentic.orchestrator.context;

/** A versioned, hashed artifact produced by a node (file path relative to the workspace). */
public record ArtifactRef(String path, String sha256, int version, String producerNode) { }
