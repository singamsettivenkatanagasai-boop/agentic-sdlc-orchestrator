package com.agentic.orchestrator.workspace;

import java.nio.file.Path;

/** Strategy for building and testing the project inside a workspace directory. */
public interface BuildRunner {
    BuildResult build(Path root);

    TestResult test(Path root);
}
