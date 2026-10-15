package com.agentic.orchestrator.agents;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * A verified change set (full-file overlays) shipped on the classpath, used by the deterministic
 * implementation of the brownfield enhancement. Files are stored with a {@code .txt} suffix so
 * they are not compiled into the orchestrator; {@code MANIFEST.txt} lists the target paths.
 */
public final class PatchBundle {
    private PatchBundle() { }

    public static Map<String, String> load(String name) {
        String base = "/bundles/" + name + "/";
        Map<String, String> files = new TreeMap<>();
        for (String path : readLines(base + "MANIFEST.txt")) {
            files.put(path, readAll(base + path + ".txt"));
        }
        return files;
    }

    private static java.util.List<String> readLines(String resource) {
        return java.util.Arrays.stream(readAll(resource).split("\\R"))
                .map(String::trim).filter(s -> !s.isEmpty() && !s.startsWith("#")).toList();
    }

    private static String readAll(String resource) {
        try (InputStream in = PatchBundle.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing bundle resource: " + resource);
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[4096];
                int n;
                while ((n = r.read(buf)) > 0) {
                    sb.append(buf, 0, n);
                }
                return sb.toString();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
