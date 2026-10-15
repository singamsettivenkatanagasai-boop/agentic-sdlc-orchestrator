package com.agentic.orchestrator.agents;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The verified, known-good reference implementation of the URL shortener (the project under
 * {@code target-apps/url-shortener}). It is the deterministic fallback of the Developer agent
 * and the seed of the brownfield workspace. The orchestrator only reads it as files; it has no
 * compile-time dependency on the project.
 */
public class GoldenBundle {
    private static final Set<String> SKIPPED = Set.of("target", ".git", ".idea");

    private final Path root;

    public GoldenBundle(Path root) { this.root = root; }

    public Path root() { return root; }

    public boolean available() { return Files.isRegularFile(root.resolve("pom.xml")); }

    /** pom.xml and everything under src/main. */
    public Map<String, String> mainFiles() {
        return collect(p -> p.equals("pom.xml") || p.startsWith("src/main/"));
    }

    /** Everything under src/test. */
    public Map<String, String> testFiles() {
        return collect(p -> p.startsWith("src/test/"));
    }

    /** All project files (used to seed a baseline workspace). */
    public Map<String, String> allFiles() {
        return collect(p -> p.equals("pom.xml") || p.startsWith("src/"));
    }

    private Map<String, String> collect(Predicate<String> filter) {
        if (!available()) {
            throw new IllegalStateException("golden bundle not found at " + root
                    + " (expected target-apps/url-shortener/pom.xml)");
        }
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                Path rel = root.relativize(p);
                for (Path part : rel) {
                    if (SKIPPED.contains(part.toString())) {
                        return;
                    }
                }
                String relPath = rel.toString().replace('\\', '/');
                if (filter.test(relPath)) {
                    try {
                        files.put(relPath, Files.readString(p, StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files;
    }
}
