package com.agentic.orchestrator.audit;

import com.agentic.orchestrator.util.Hashing;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Append-only, hash-chained event log of one run, persisted as JSON lines. Persistence is
 * fail-closed: if an event cannot be written the exception propagates and the run stops,
 * because executing without an audit trail would violate the compliance guardrail.
 */
public final class EventLog {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String GENESIS = "0".repeat(64);

    private final String runId;
    private final Path file;
    private final List<RunEvent> events = new ArrayList<>();

    /** @param file JSONL target; null keeps the log in memory only (unit tests) */
    public EventLog(String runId, Path file) {
        this.runId = runId;
        this.file = file;
        if (file != null) {
            try {
                Files.createDirectories(file.getParent());
                Files.deleteIfExists(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public synchronized RunEvent append(String nodeId, EventType type, String actor, String detail) {
        long seq = events.size() + 1L;
        Instant ts = Instant.now();
        String prev = events.isEmpty() ? GENESIS : events.get(events.size() - 1).hash();
        String hash = hashOf(seq, ts, runId, nodeId, type, actor, detail, prev);
        RunEvent e = new RunEvent(seq, ts, runId, nodeId, type, actor, detail, prev, hash);
        events.add(e);
        persist(e);
        return e;
    }

    public synchronized List<RunEvent> events() { return List.copyOf(events); }
    public Path file() { return file; }

    /** Recomputes the whole chain; false means the log was altered or reordered. */
    public static boolean verify(List<RunEvent> events) {
        String prev = GENESIS;
        for (RunEvent e : events) {
            if (!e.prevHash().equals(prev)) {
                return false;
            }
            String expected = hashOf(e.seq(), e.ts(), e.runId(), e.nodeId(), e.type(), e.actor(), e.detail(), e.prevHash());
            if (!expected.equals(e.hash())) {
                return false;
            }
            prev = e.hash();
        }
        return true;
    }

    private static String hashOf(long seq, Instant ts, String runId, String nodeId, EventType type,
                                 String actor, String detail, String prev) {
        return Hashing.sha256(String.join("|", String.valueOf(seq), ts.toString(), runId,
                String.valueOf(nodeId), type.name(), actor, detail, prev));
    }

    private void persist(RunEvent e) {
        if (file == null) {
            return;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seq", e.seq());
        m.put("ts", e.ts().toString());
        m.put("runId", e.runId());
        m.put("nodeId", e.nodeId());
        m.put("type", e.type().name());
        m.put("actor", e.actor());
        m.put("detail", e.detail());
        m.put("prevHash", e.prevHash());
        m.put("hash", e.hash());
        try {
            Files.writeString(file, JSON.writeValueAsString(m) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            throw new UncheckedIOException("audit log write failed", ex);
        }
    }
}
