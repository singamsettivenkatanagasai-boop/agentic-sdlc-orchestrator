package com.agentic.orchestrator.workspace;

import com.agentic.orchestrator.model.PolicyViolationException;
import com.agentic.orchestrator.support.FakeBuildRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalWorkspaceTest {
    @TempDir
    Path tmp;

    @Test
    void readWriteListDelete() {
        LocalWorkspace ws = new LocalWorkspace(tmp, new FakeBuildRunner(), new JGitCheckpointStore());
        ws.write("docs/a.md", "a");
        ws.write("src/main/java/X.java", "x");
        assertTrue(ws.exists("docs/a.md"));
        assertEquals("a", ws.read("docs/a.md"));
        assertEquals(List.of("docs/a.md"), ws.list("docs/**"));
        assertEquals(List.of("src/main/java/X.java"), ws.list("src/main/**"));
        ws.delete("docs/a.md");
        assertFalse(ws.exists("docs/a.md"));
    }

    @Test
    void pathsCannotEscapeTheRoot() {
        LocalWorkspace ws = new LocalWorkspace(tmp, new FakeBuildRunner(), new JGitCheckpointStore());
        assertThrows(PolicyViolationException.class, () -> ws.write("../x.txt", "x"));
        assertThrows(PolicyViolationException.class, () -> ws.read("../../etc/passwd"));
    }

    @Test
    void checkpointAndRestoreBringBackAnEarlierState() {
        LocalWorkspace ws = new LocalWorkspace(tmp, new FakeBuildRunner(), new JGitCheckpointStore());
        ws.write("docs/a.md", "v1");
        String cp = ws.checkpoint("first");
        ws.write("docs/a.md", "v2");
        ws.write("docs/b.md", "untracked later");
        ws.restore(cp);
        assertEquals("v1", ws.read("docs/a.md"));
        assertFalse(ws.exists("docs/b.md"));
    }

    @Test
    void laterCheckpointsAreDistinctCommits() {
        LocalWorkspace ws = new LocalWorkspace(tmp, new FakeBuildRunner(), new JGitCheckpointStore());
        ws.write("a.txt", "1");
        String first = ws.checkpoint("one");
        ws.write("a.txt", "2");
        String second = ws.checkpoint("two");
        assertTrue(!first.equals(second));
        ws.restore(first);
        assertEquals("1", ws.read("a.txt"));
    }

    @Test
    void listingIgnoresGitAndBuildOutputDirectories() throws Exception {
        LocalWorkspace ws = new LocalWorkspace(tmp, new FakeBuildRunner(), new JGitCheckpointStore());
        ws.write("src/main/A.java", "a");
        ws.checkpoint("creates .git");
        java.nio.file.Files.createDirectories(tmp.resolve("target/classes"));
        java.nio.file.Files.writeString(tmp.resolve("target/classes/A.class"), "x");
        List<String> all = ws.list("**");
        assertTrue(all.contains("src/main/A.java"));
        assertTrue(all.stream().noneMatch(f -> f.startsWith(".git/") || f.startsWith("target/")), all.toString());
    }
}
