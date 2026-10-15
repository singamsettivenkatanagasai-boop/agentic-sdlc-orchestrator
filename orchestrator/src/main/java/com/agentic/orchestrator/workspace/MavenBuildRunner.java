package com.agentic.orchestrator.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Builds and tests a Maven project by invoking the {@code mvn} command in the workspace. */
public final class MavenBuildRunner implements BuildRunner {
    private static final int MAX_OUTPUT = 6000;

    private final String command;
    private final boolean offline;
    private final Duration timeout;

    public MavenBuildRunner(String command, boolean offline, Duration timeout) {
        this.command = command;
        this.offline = offline;
        this.timeout = timeout;
    }

    /** Picks {@code mvn.cmd} on Windows and {@code mvn} elsewhere. */
    public static String defaultCommand() {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? "mvn.cmd" : "mvn";
    }

    @Override
    public BuildResult build(Path root) {
        Exec r = run(root, "compile");
        return new BuildResult(r.exit == 0, r.output);
    }

    @Override
    public TestResult test(Path root) {
        Exec r = run(root, "test");
        int[] counts = SurefireReports.summarize(root);
        boolean ok = r.exit == 0 && counts[1] == 0 && counts[2] == 0;
        return new TestResult(ok, counts[0], counts[1], counts[2], r.output);
    }

    private record Exec(int exit, String output) { }

    private Exec run(Path root, String goal) {
        List<String> cmd = new ArrayList<>(List.of(command, "-B", "-q"));
        if (offline) {
            cmd.add("-o");
        }
        cmd.add(goal);
        try {
            Process p = new ProcessBuilder(cmd).directory(root.toFile()).redirectErrorStream(true).start();
            CompletableFuture<String> out = CompletableFuture.supplyAsync(() -> read(p.getInputStream()));
            if (!p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                return new Exec(124, "timed out after " + timeout.toMinutes() + " minutes: " + String.join(" ", cmd));
            }
            return new Exec(p.exitValue(), tail(out.get(10, TimeUnit.SECONDS)));
        } catch (IOException e) {
            return new Exec(127, "could not start '" + command + "': " + e.getMessage()
                    + " (is Maven installed and on the PATH?)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Exec(130, "interrupted");
        } catch (Exception e) {
            return new Exec(1, "build runner error: " + e);
        }
    }

    private static String read(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String tail(String s) {
        return s.length() <= MAX_OUTPUT ? s : "..." + s.substring(s.length() - MAX_OUTPUT);
    }
}
