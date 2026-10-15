package com.agentic.orchestrator.workspace;

import java.nio.file.Path;
import java.util.List;

/**
 * The only way the orchestrator and its agents touch the target project. The platform has no
 * compile-time dependency on the project it works on, so any directory can be a target.
 *
 * <p>All paths are workspace-relative and '/'-separated.
 */
public interface Workspace {
    Path root();

    boolean exists(String path);

    String read(String path);

    void write(String path, String content);

    void delete(String path);

    /** Files matching the glob (e.g. {@code src/main/**}), sorted, excluding build output and .git. */
    List<String> list(String glob);

    BuildResult build();

    TestResult test();

    /** Records a durable snapshot of the whole workspace and returns its id. */
    String checkpoint(String label);

    /** Restores the workspace to a previous checkpoint. */
    void restore(String checkpointId);
}
