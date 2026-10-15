package com.agentic.orchestrator.workspace;

import com.agentic.orchestrator.model.PolicyViolationException;
import com.agentic.orchestrator.util.Globs;
import com.agentic.orchestrator.util.PathSafety;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Workspace backed by a directory on the local disk. */
public final class LocalWorkspace implements Workspace {
    private static final Set<String> SKIPPED_DIRS = Set.of(".git", "target", ".idea");

    private final Path root;
    private final BuildRunner runner;
    private final CheckpointStore checkpoints;

    public LocalWorkspace(Path root, BuildRunner runner, CheckpointStore checkpoints) {
        this.root = root.toAbsolutePath().normalize();
        this.runner = runner;
        this.checkpoints = checkpoints;
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Path root() { return root; }

    private Path resolve(String path) {
        String rel = PathSafety.normalize(path);
        Path target = root.resolve(rel).normalize();
        if (!target.startsWith(root)) {
            throw new PolicyViolationException("workspace-containment", "path escapes the workspace: " + path, true);
        }
        return target;
    }

    @Override
    public boolean exists(String path) { return Files.isRegularFile(resolve(path)); }

    @Override
    public String read(String path) {
        try {
            return Files.readString(resolve(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
    }

    @Override
    public void write(String path, String content) {
        Path target = resolve(path);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + path, e);
        }
    }

    @Override
    public void delete(String path) {
        try {
            Files.deleteIfExists(resolve(path));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot delete " + path, e);
        }
    }

    /**
     * Lists files matching {@code glob}. Directories such as {@code .git} and {@code target} are
     * never entered: they are not part of the project, they are large, and files inside them
     * (git lock files, compiler output) appear and vanish while other workers run in parallel.
     * A file that disappears during the walk is skipped instead of failing the caller.
     */
    @Override
    public List<String> list(String glob) {
        List<String> found = new java.util.ArrayList<>();
        try {
            Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<Path>() {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                    boolean skip = !dir.equals(root) && SKIPPED_DIRS.contains(dir.getFileName().toString());
                    return skip ? java.nio.file.FileVisitResult.SKIP_SUBTREE : java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        String rel = root.relativize(file).toString().replace('\\', '/');
                        if (Globs.matches(glob, rel)) {
                            found.add(rel);
                        }
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        java.util.Collections.sort(found);
        return found;
    }

    @Override
    public BuildResult build() { return runner.build(root); }

    @Override
    public TestResult test() { return runner.test(root); }

    @Override
    public String checkpoint(String label) {
        try {
            return checkpoints.checkpoint(root, label);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void restore(String checkpointId) {
        try {
            checkpoints.restore(root, checkpointId);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
