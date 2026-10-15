package com.agentic.orchestrator.workspace;

import java.io.IOException;
import java.nio.file.Path;

/** Durable snapshots of a workspace (JGit commits in production, directory copies in tests). */
public interface CheckpointStore {
    String checkpoint(Path root, String label) throws IOException;

    void restore(Path root, String checkpointId) throws IOException;
}
