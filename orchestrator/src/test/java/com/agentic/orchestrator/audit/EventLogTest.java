package com.agentic.orchestrator.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventLogTest {
    @TempDir
    Path tmp;

    private static EventLog sample() {
        EventLog log = new EventLog("r1", null);
        log.append("run", EventType.RUN_STARTED, "orchestrator", "start");
        log.append("n1", EventType.AGENT, "developer", "did it");
        log.append("n1", EventType.GATE, "orchestrator", "passed");
        return log;
    }

    @Test
    void chainOfUntouchedEventsVerifies() {
        assertTrue(EventLog.verify(sample().events()));
        assertTrue(EventLog.verify(List.of()));
    }

    @Test
    void editingAnEventBreaksTheChain() {
        List<RunEvent> events = new ArrayList<>(sample().events());
        RunEvent e = events.get(1);
        events.set(1, new RunEvent(e.seq(), e.ts(), e.runId(), e.nodeId(), e.type(), e.actor(),
                "tampered", e.prevHash(), e.hash()));
        assertFalse(EventLog.verify(events));
    }

    @Test
    void removingOrReorderingEventsBreaksTheChain() {
        List<RunEvent> events = new ArrayList<>(sample().events());
        events.remove(1);
        assertFalse(EventLog.verify(events));
        List<RunEvent> swapped = new ArrayList<>(sample().events());
        RunEvent first = swapped.get(0);
        swapped.set(0, swapped.get(1));
        swapped.set(1, first);
        assertFalse(EventLog.verify(swapped));
    }

    @Test
    void eventsArePersistedAsJsonLines() throws Exception {
        Path file = tmp.resolve("sub/events.jsonl");
        EventLog log = new EventLog("r2", file);
        log.append("n", EventType.AGENT, "a", "one");
        log.append("n", EventType.AGENT, "a", "two");
        List<String> lines = Files.readAllLines(file);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("\"detail\":\"one\""));
    }
}
