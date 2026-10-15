package com.agentic.orchestrator.workspace;

import com.agentic.orchestrator.governance.AutonomyBoundaries;
import com.agentic.orchestrator.governance.PolicyEngine;
import com.agentic.orchestrator.model.PolicyViolationException;
import com.agentic.orchestrator.support.FakeBuildRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardedWorkspaceTest {
    @TempDir
    Path tmp;

    LocalWorkspace real;
    AutonomyBoundaries boundaries = new AutonomyBoundaries();
    PolicyEngine policy = new PolicyEngine();

    @BeforeEach
    void setUp() {
        real = new LocalWorkspace(tmp, new FakeBuildRunner(), new JGitCheckpointStore());
    }

    private GuardedWorkspace guard(String agent) {
        return new GuardedWorkspace(real, agent, boundaries.forAgent(agent), policy);
    }

    @Test
    void agentsMayWriteInsideTheirBoundary() {
        GuardedWorkspace g = guard("docs");
        g.write("README.md", "hello");
        g.write("docs/a.md", "a");
        assertEquals("hello", real.read("README.md"));
        assertEquals(2, g.changedPaths().size());
    }

    @Test
    void writesOutsideTheBoundaryAreDeniedAndNotFatal() {
        PolicyViolationException e = assertThrows(PolicyViolationException.class,
                () -> guard("docs").write("src/main/java/Evil.java", "class Evil {}"));
        assertEquals("autonomy-boundary", e.rule());
        assertFalse(e.fatal());
        assertFalse(real.exists("src/main/java/Evil.java"));
    }

    @Test
    void unknownAgentsAreReadOnly() {
        assertThrows(PolicyViolationException.class, () -> guard("stranger").write("docs/x.md", "x"));
    }

    @Test
    void pathEscapesAreFatal() {
        PolicyViolationException e = assertThrows(PolicyViolationException.class,
                () -> guard("developer").write("../outside.txt", "x"));
        assertTrue(e.fatal());
        assertThrows(PolicyViolationException.class, () -> guard("developer").write("/etc/passwd", "x"));
    }

    @Test
    void secretsAreFatalAndNeverReachTheDisk() {
        PolicyViolationException e = assertThrows(PolicyViolationException.class,
                () -> guard("developer").write("src/main/resources/a.properties", "k=" + "AKIA" + "IOSFODNN7EXAMPLE"));
        assertTrue(e.fatal());
        assertFalse(real.exists("src/main/resources/a.properties"));
    }

    @Test
    void fileCountIsLimited() {
        GuardedWorkspace g = guard("requirements");   // max 5 files
        for (int i = 0; i < 5; i++) {
            g.write("docs/f" + i + ".md", "x");
        }
        assertThrows(PolicyViolationException.class, () -> g.write("docs/f5.md", "x"));
        g.write("docs/f0.md", "rewrite is fine");
    }

    @Test
    void rollbackRestoresOldContentAndRemovesNewFiles() {
        real.write("docs/existing.md", "original");
        GuardedWorkspace g = guard("docs");
        g.write("docs/existing.md", "changed");
        g.write("docs/new.md", "new");
        g.rollback();
        assertEquals("original", real.read("docs/existing.md"));
        assertFalse(real.exists("docs/new.md"));
        assertTrue(g.changedPaths().isEmpty());
    }

    @Test
    void agentsCannotCreateCheckpoints() {
        assertThrows(UnsupportedOperationException.class, () -> guard("docs").checkpoint("x"));
    }
}
