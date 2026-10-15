package com.agentic.orchestrator.graph;

import com.agentic.orchestrator.context.DecisionRecord;
import com.agentic.orchestrator.workspace.BuildResult;
import com.agentic.orchestrator.workspace.TestResult;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Library of reusable gates. */
public final class Gates {
    private Gates() { }

    /** Entry gate: every named artifact must already be registered in the run context. */
    public static Gate requireArtifacts(String... paths) {
        return new Gate("inputs-available:" + String.join(",", paths), ctx -> {
            for (String p : paths) {
                if (ctx.context().artifact(p) == null) {
                    return GateResult.fail("required input artifact missing: " + p);
                }
            }
            return GateResult.pass();
        });
    }

    /** Exit gate: every named file must exist in the workspace. */
    public static Gate producesFiles(String... paths) {
        return new Gate("outputs-exist:" + String.join(",", Arrays.asList(paths)), ctx -> {
            for (String p : paths) {
                if (!ctx.workspace().exists(p)) {
                    return GateResult.fail("expected output not produced: " + p);
                }
            }
            return GateResult.pass();
        });
    }

    /** Exit gate: the project in the workspace must compile. */
    public static Gate buildPasses() {
        return new Gate("build-passes", ctx -> {
            BuildResult r = ctx.workspace().build();
            return r.success() ? GateResult.pass() : GateResult.fail("build failed:\n" + r.output());
        });
    }

    /** Exit gate: the test agent must have recorded a successful test run. */
    public static Gate testsPass() {
        return new Gate("tests-pass", ctx -> {
            Object o = ctx.context().attribute("testResult");
            if (!(o instanceof TestResult tr)) {
                return GateResult.fail("no test result recorded");
            }
            return tr.success() ? GateResult.pass()
                    : GateResult.fail("tests failed: " + tr.summary() + "\n" + tr.output());
        });
    }

    /** Compliance gate: every other node must have left at least one decision record. */
    public static Gate decisionsRecorded() {
        return new Gate("decisions-recorded", ctx -> {
            Set<String> have = ctx.context().decisions().stream()
                    .map(DecisionRecord::nodeId).collect(Collectors.toSet());
            List<String> missing = ctx.graph().nodes().stream()
                    .map(TaskNode::id)
                    .filter(id -> !id.equals(ctx.node().id()))
                    .filter(id -> !have.contains(id))
                    .toList();
            return missing.isEmpty() ? GateResult.pass()
                    : GateResult.fail("no decision record for nodes: " + missing);
        });
    }
}
