package com.agentic.orchestrator.planner;

import com.agentic.orchestrator.agents.GoldenDeveloperAgent;
import com.agentic.orchestrator.agents.Interpretations;
import com.agentic.orchestrator.context.RunContext;
import com.agentic.orchestrator.graph.Gates;
import com.agentic.orchestrator.graph.Risk;
import com.agentic.orchestrator.graph.TaskNode;
import com.agentic.orchestrator.graph.WorkflowGraph;
import com.agentic.orchestrator.model.Scenario;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a scenario into an explicit dependency graph and grows the graph while the run
 * progresses (dynamic planning). Everything that makes the SDLC "agentic" and not a script is
 * visible here: which steps exist, what each one needs before it starts (entry gate), what must
 * be true before its output is accepted (exit gate), how risky it is, and where it can run in
 * parallel.
 *
 * <pre>
 * greenfield:  requirements -> design(HIGH) -.
 *                           \-> test-plan ----+-> implement -> tests -> security -.
 *                                                                          \-> docs -+-> release(HIGH) -> signoff(HIGH)
 * brownfield:  requirements, impact -> design(HIGH) [-> migration(HIGH), added when impact finds a schema change] -> implement ...
 * ambiguous:   interpret(human) -> plan-&lt;id&gt; per confirmed interpretation (added at run time) -> scoped-plan -> signoff
 * </pre>
 */
public class Planner {

    public WorkflowGraph plan(Scenario scenario) {
        WorkflowGraph g = new WorkflowGraph();
        switch (scenario) {
            case GREENFIELD -> {
                g.add(requirements());
                addDeliveryChain(g, false);
            }
            case BROWNFIELD -> {
                g.add(requirements());
                g.add(TaskNode.builder("impact", "architect")
                        .description("Analyse the existing code base and assess the impact of the change")
                        .risk(Risk.LOW).param("mode", "impact")
                        .exitGate(Gates.producesFiles("docs/impact-analysis.md")).build());
                addDeliveryChain(g, true);
            }
            case AMBIGUOUS -> {
                g.add(TaskNode.builder("interpret", "requirements")
                        .description("List the possible readings of the vague requirement and ask a human which are in scope")
                        .risk(Risk.LOW).param("mode", "interpret").humanInput(true)
                        .exitGate(Gates.producesFiles("docs/interpretations.md")).build());
                g.add(TaskNode.builder("scoped-plan", "scope-planner")
                        .description("Merge the plans of the confirmed interpretations into one scoped plan")
                        .dependsOn("interpret").risk(Risk.MEDIUM).param("mode", "merge")
                        .exitGate(Gates.producesFiles("docs/scoped-plan.md")).build());
                g.add(TaskNode.builder("signoff", "reviewer")
                        .description("Human sign-off of the scoped plan")
                        .dependsOn("scoped-plan").risk(Risk.HIGH)
                        .exitGate(Gates.decisionsRecorded()).build());
            }
        }
        g.validate();
        return g;
    }

    private static TaskNode requirements() {
        return TaskNode.builder("requirements", "requirements")
                .description("Normalise the requirement, list acceptance criteria and record assumptions")
                .risk(Risk.LOW).param("mode", "analyze")
                .exitGate(Gates.producesFiles("docs/requirements.md")).build();
    }

    /** The common design -> build -> verify -> release chain of greenfield and brownfield. */
    private void addDeliveryChain(WorkflowGraph g, boolean brownfield) {
        List<String> designDeps = brownfield ? List.of("requirements", "impact") : List.of("requirements");
        g.add(TaskNode.builder("design", "architect")
                .description("Design the solution and the OpenAPI contract")
                .dependsOn(designDeps.toArray(String[]::new)).risk(Risk.HIGH).param("mode", "design")
                .entryGate(Gates.requireArtifacts("docs/requirements.md"))
                .exitGate(Gates.producesFiles("docs/design.md", "openapi.yaml")).build());
        g.add(TaskNode.builder("test-plan", "test")
                .description("Plan the tests from the requirement, in parallel with the design")
                .dependsOn("requirements").risk(Risk.LOW).param("mode", "plan")
                .exitGate(Gates.producesFiles("docs/test-plan.md")).build());
        g.add(TaskNode.builder("implement", "developer")
                .description(brownfield ? "Implement the change in the existing code base" : "Implement the service")
                .dependsOn("design").risk(Risk.MEDIUM).maxAttempts(3).fallbackAgent("golden-developer")
                .param("mode", brownfield ? "brownfield" : "greenfield")
                .entryGate(Gates.requireArtifacts("docs/design.md", "openapi.yaml"))
                .exitGate(Gates.buildPasses()).build());
        g.add(TaskNode.builder("tests", "test")
                .description("Write and run the automated tests")
                .dependsOn("implement", "test-plan").risk(Risk.MEDIUM).maxAttempts(2).param("mode", "write-and-run")
                .exitGate(Gates.testsPass()).build());
        g.add(TaskNode.builder("security", "security")
                .description("Security and compliance review")
                .dependsOn("tests").risk(Risk.MEDIUM)
                .exitGate(Gates.producesFiles("docs/security-review.md")).build());
        g.add(TaskNode.builder("docs", "docs")
                .description("Generate README and changelog from the code")
                .dependsOn("tests").risk(Risk.LOW)
                .exitGate(Gates.producesFiles("README.md", "docs/CHANGELOG.md")).build());
        g.add(TaskNode.builder("release", "release")
                .description("Release readiness check and release notes")
                .dependsOn("security", "docs").risk(Risk.HIGH)
                .exitGate(Gates.producesFiles("docs/release-notes.md")).build());
        g.add(TaskNode.builder("signoff", "reviewer")
                .description("Engineering summary and final human sign-off")
                .dependsOn("release").risk(Risk.HIGH)
                .exitGate(Gates.decisionsRecorded()).build());
    }

    /**
     * Called by the engine after a node has been accepted. May add nodes and dependencies based
     * on what the node discovered. Returns the ids of nodes added (empty if the plan is unchanged).
     */
    public List<String> expandAfter(TaskNode done, Scenario scenario, RunContext ctx, WorkflowGraph graph) {
        List<String> added = new ArrayList<>();
        if (scenario == Scenario.BROWNFIELD && done.id().equals("impact")
                && Boolean.TRUE.equals(ctx.attribute("impact.schemaChange")) && !graph.has("migration")) {
            graph.add(TaskNode.builder("migration", "golden-developer")
                    .description("Database migration for the new column (added because impact analysis found a schema change)")
                    .dependsOn("design").risk(Risk.HIGH).param("mode", "migration")
                    .exitGate(Gates.producesFiles(GoldenDeveloperAgent.MIGRATION_PATH)).build());
            graph.addDependency("implement", "migration");
            added.add("migration");
        }
        if (scenario == Scenario.AMBIGUOUS && done.id().equals("interpret")
                && ctx.attribute("confirmedInterpretations") instanceof List<?> confirmed) {
            for (Object o : confirmed) {
                String id = String.valueOf(o);
                if (Interpretations.find(id).isEmpty() || graph.has("plan-" + id)) {
                    continue;
                }
                graph.add(TaskNode.builder("plan-" + id, "scope-planner")
                        .description("Plan for the confirmed interpretation '" + id + "'")
                        .dependsOn("interpret").risk(Risk.LOW).param("mode", "plan").param("interpretation", id)
                        .exitGate(Gates.producesFiles("docs/plan-" + id + ".md")).build());
                graph.addDependency("scoped-plan", "plan-" + id);
                added.add("plan-" + id);
            }
        }
        return added;
    }
}
