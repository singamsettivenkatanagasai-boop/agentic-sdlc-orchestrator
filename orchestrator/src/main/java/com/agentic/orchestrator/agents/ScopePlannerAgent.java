package com.agentic.orchestrator.agents;

import com.agentic.orchestrator.agents.Interpretations.Interpretation;
import com.agentic.orchestrator.util.Md;

import java.util.ArrayList;
import java.util.List;

/**
 * Planner for the ambiguous scenario. Mode {@code plan} writes a plan for one human-confirmed
 * interpretation ({@code plan-<id>}, runs in parallel with the others); mode {@code merge} joins
 * those plans into a single scoped plan and lists what was deliberately left out.
 */
public class ScopePlannerAgent implements Agent {
    @Override
    public String name() { return "scope-planner"; }

    @Override
    public AgentResult execute(AgentTask t) {
        return "merge".equals(t.node().param("mode", "plan")) ? merge(t) : plan(t);
    }

    private AgentResult plan(AgentTask t) {
        String id = t.node().param("interpretation", "");
        Interpretation i = Interpretations.find(id).orElseThrow(() -> new IllegalStateException("unknown interpretation: " + id));
        StringBuilder p = new StringBuilder("# Plan: ").append(i.title()).append("\n\n");
        p.append("**Meaning:** ").append(i.meaning()).append("\n\n## Tasks\n\n").append(Md.bullets(i.tasks()));
        p.append("\n**Risk:** ").append(i.risk()).append("\n");
        t.workspace().write("docs/plan-" + id + ".md", p.toString());
        return new AgentResult("planned '" + i.title() + "' with " + i.tasks().size() + " tasks",
                "planned only because a human confirmed this interpretation");
    }

    private AgentResult merge(AgentTask t) {
        List<String> inScope = new ArrayList<>();
        List<String> outOfScope = new ArrayList<>();
        for (Interpretation i : Interpretations.CATALOG) {
            if (t.workspace().exists("docs/plan-" + i.id() + ".md")) {
                inScope.add(i.title() + " (" + i.risk() + ")");
            } else {
                outOfScope.add(i.title() + " - not confirmed by a human");
            }
        }
        StringBuilder s = new StringBuilder("# Scoped plan\n\n## In scope\n\n").append(Md.bullets(inScope));
        s.append("\n## Out of scope\n\n").append(outOfScope.isEmpty() ? "_Nothing._\n" : Md.bullets(outOfScope));
        s.append("\n## Suggested order\n\nSmallest-risk items first: rate limiting, then expiry (schema change), then anything that needs a new external dependency.\n");
        t.workspace().write("docs/scoped-plan.md", s.toString());
        return new AgentResult("scoped plan: " + inScope.size() + " in scope, " + outOfScope.size() + " excluded",
                "the human-confirmed scope bounds the plan; excluded items are listed explicitly");
    }
}
