package com.agentic.orchestrator.config;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Finds the repository root. {@code mvn -pl orchestrator spring-boot:run} starts the JVM inside
 * the module directory, so relative paths like {@code target-apps/...} must not depend on the
 * current directory: walk upwards until the reference application is found.
 */
public final class RepoLocator {
    private RepoLocator() { }

    public static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path p = dir; p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("target-apps/url-shortener/pom.xml"))) {
                return p;
            }
        }
        return dir;
    }

    /** Resolves {@code path} against the repository root unless it is already absolute. */
    public static Path resolve(String path) {
        Path p = Path.of(path);
        return p.isAbsolute() ? p : repoRoot().resolve(p).normalize();
    }
}
