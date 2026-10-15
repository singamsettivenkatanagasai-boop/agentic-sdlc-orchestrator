package com.agentic.orchestrator.agents;

import com.agentic.orchestrator.workspace.Workspace;

import java.util.Map;

/**
 * Deterministic, verified implementation of each development mode. It is the fallback the
 * orchestrator switches to when the primary Developer agent exhausts its retries, and it is also
 * what the Developer agent replays when no language model is configured.
 *
 * <ul>
 *   <li>{@code greenfield}: the reference URL shortener (pom.xml and src/main)</li>
 *   <li>{@code brownfield}: the verified expiry + per-IP rate limit change set</li>
 *   <li>{@code migration}: the SQL migration for the new expiry column</li>
 * </ul>
 */
public class GoldenDeveloperAgent implements Agent {
    public static final String PATCH = "expiry-ratelimit";
    public static final String MIGRATION_PATH = "src/main/resources/db/migration/V2__add_link_expiry.sql";

    private static final String MIGRATION_SQL = String.join("\n",
            "-- V2: optional link expiry",
            "-- The prototype lets Hibernate apply this (ddl-auto=update); in production run it with a migration tool.",
            "ALTER TABLE links ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE NULL;",
            "CREATE INDEX idx_links_expires_at ON links (expires_at);",
            "");

    private final GoldenBundle golden;

    public GoldenDeveloperAgent(GoldenBundle golden) { this.golden = golden; }

    @Override
    public String name() { return "golden-developer"; }

    @Override
    public AgentResult execute(AgentTask t) {
        String mode = t.node().param("mode", "greenfield");
        int n = apply(t.workspace(), mode);
        boolean asFallback = !t.node().agent().equals(name());
        return new AgentResult("applied the verified '" + mode + "' implementation (" + n + " files)",
                asFallback ? "fallback to the known-good implementation after the primary agent could not satisfy the gates"
                        : "deterministic change set; the need for it was established by the impact analysis");
    }

    /** Writes the verified files for {@code mode}; returns the number of files written. */
    public int apply(Workspace ws, String mode) {
        switch (mode) {
            case "greenfield" -> {
                Map<String, String> files = golden.mainFiles();
                files.forEach(ws::write);
                return files.size();
            }
            case "brownfield" -> {
                Map<String, String> patch = PatchBundle.load(PATCH);
                int[] n = {0};
                patch.forEach((path, content) -> {
                    if (!path.startsWith("src/test/")) {
                        ws.write(path, content);
                        n[0]++;
                    }
                });
                return n[0];
            }
            case "migration" -> {
                ws.write(MIGRATION_PATH, MIGRATION_SQL);
                return 1;
            }
            default -> throw new IllegalArgumentException("unknown developer mode: " + mode);
        }
    }
}
