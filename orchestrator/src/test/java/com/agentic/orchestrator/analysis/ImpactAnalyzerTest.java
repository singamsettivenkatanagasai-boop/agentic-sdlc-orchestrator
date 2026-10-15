package com.agentic.orchestrator.analysis;

import com.agentic.orchestrator.agents.GoldenBundle;
import com.agentic.orchestrator.analysis.ImpactAnalyzer.CodeModel;
import com.agentic.orchestrator.analysis.ImpactAnalyzer.ImpactReport;
import com.agentic.orchestrator.config.RepoLocator;
import com.agentic.orchestrator.support.FakeBuildRunner;
import com.agentic.orchestrator.workspace.JGitCheckpointStore;
import com.agentic.orchestrator.workspace.LocalWorkspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The analyzer must understand the real baseline application, not a toy fixture. */
class ImpactAnalyzerTest {
    @TempDir
    Path tmp;

    LocalWorkspace ws;
    final ImpactAnalyzer analyzer = new ImpactAnalyzer();

    @BeforeEach
    void seed() {
        ws = new LocalWorkspace(tmp, new FakeBuildRunner(), new JGitCheckpointStore());
        new GoldenBundle(RepoLocator.resolve("target-apps/url-shortener")).allFiles().forEach(ws::write);
    }

    @Test
    void findsTheEndpointsOfTheBaselineApplication() {
        CodeModel model = analyzer.scan(ws);
        assertTrue(model.endpoints().stream().anyMatch(e -> e.method().equals("POST") && e.path().equals("/api/v1/links")),
                model.endpoints().toString());
        assertTrue(model.endpoints().stream().anyMatch(e -> e.path().equals("/{code:[A-Za-z0-9_-]+}")),
                model.endpoints().toString());
        assertEquals(5, model.endpoints().size(), model.endpoints().toString());
        assertTrue(model.warnings().isEmpty(), model.warnings().toString());
    }

    @Test
    void classifiesEntitiesAndServices() {
        CodeModel model = analyzer.scan(ws);
        assertEquals(2, model.ofKind("entity").size());
        assertEquals(2, model.ofKind("controller").size());
        assertTrue(model.ofKind("service").stream().anyMatch(c -> c.name().equals("LinkService")));
    }

    @Test
    void expiryRequirementImpactsTheEntityAndFlagsASchemaChange() {
        ImpactReport r = analyzer.analyze(ws, "Add optional link expiry to the shortener");
        assertTrue(r.schemaChange());
        assertTrue(r.apiChange());
        assertTrue(r.impacted().stream().anyMatch(i -> i.component().contains("Link")), r.impacted().toString());
    }

    @Test
    void rateLimitOnlyRequirementIsNotASchemaChange() {
        ImpactReport r = analyzer.analyze(ws, "Add per-IP rate limiting");
        assertFalse(r.schemaChange());
    }
}
