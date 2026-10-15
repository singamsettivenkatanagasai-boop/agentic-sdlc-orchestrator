package com.agentic.orchestrator.support;

import com.agentic.orchestrator.workspace.BuildResult;
import com.agentic.orchestrator.workspace.BuildRunner;
import com.agentic.orchestrator.workspace.TestResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Stand-in for Maven so engine and scenario tests run in milliseconds and without network. It
 * "fails to compile" exactly when the injected defect is present, and "runs" the tests by
 * counting {@code @Test} methods. The real Maven runner is exercised by the CI build of the
 * generated project and by the scenario scripts.
 */
public final class FakeBuildRunner implements BuildRunner {
    public final AtomicInteger builds = new AtomicInteger();
    public final AtomicInteger testRuns = new AtomicInteger();

    @Override
    public BuildResult build(Path root) {
        builds.incrementAndGet();
        if (!Files.isRegularFile(root.resolve("pom.xml"))) {
            return new BuildResult(false, "no pom.xml in the workspace");
        }
        for (String f : files(root.resolve("src/main"))) {
            if (read(root.resolve("src/main").resolve(f)).contains("not an int")) {
                return new BuildResult(false, f + ":3: error: incompatible types: String cannot be converted to int");
            }
        }
        return new BuildResult(true, "BUILD SUCCESS (fake)");
    }

    @Override
    public TestResult test(Path root) {
        testRuns.incrementAndGet();
        BuildResult b = build(root);
        if (!b.success()) {
            return new TestResult(false, 0, 0, 1, b.output());
        }
        int tests = 0;
        for (String f : files(root.resolve("src/test"))) {
            String content = read(root.resolve("src/test").resolve(f));
            tests += content.split("@Test", -1).length - 1;
        }
        return tests == 0 ? new TestResult(false, 0, 0, 0, "no tests found")
                : new TestResult(true, tests, 0, 0, "tests passed (fake)");
    }

    private static java.util.List<String> files(Path dir) {
        if (!Files.isDirectory(dir)) {
            return java.util.List.of();
        }
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".java")).map(p -> dir.relativize(p).toString()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
