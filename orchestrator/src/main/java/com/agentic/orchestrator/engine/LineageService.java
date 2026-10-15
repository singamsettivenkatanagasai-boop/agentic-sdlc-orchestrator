package com.agentic.orchestrator.engine;

import com.agentic.orchestrator.context.ArtifactRef;
import com.agentic.orchestrator.context.DecisionRecord;
import com.agentic.orchestrator.context.RunContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Answers "why does this file look like this?" by walking the decision lineage backwards:
 * artifact -> decisions that produced it -> the input artifacts of those decisions -> ...
 */
public final class LineageService {

    /** One step of the trace. {@code inputs} are the traces of the artifacts the producer consumed. */
    public record Trace(String path, String sha256, int version, String producerNode,
                        List<DecisionRecord> decisions, List<Trace> inputs) { }

    public Trace trace(RunContext ctx, String path) {
        return trace(ctx, path, new HashSet<>());
    }

    private Trace trace(RunContext ctx, String path, Set<String> visited) {
        ArtifactRef ref = ctx.artifact(path);
        if (ref == null) {
            return null;
        }
        List<DecisionRecord> produced = new ArrayList<>();
        for (DecisionRecord d : ctx.decisions()) {
            if (d.outputs().containsKey(path)) {
                produced.add(d);
            }
        }
        List<Trace> inputs = new ArrayList<>();
        if (visited.add(path)) {
            Set<String> inputPaths = new HashSet<>();
            produced.forEach(d -> inputPaths.addAll(d.inputs().keySet()));
            inputPaths.stream().sorted().forEach(p -> {
                Trace t = trace(ctx, p, visited);
                if (t != null) {
                    inputs.add(t);
                }
            });
        }
        return new Trace(path, ref.sha256(), ref.version(), ref.producerNode(), produced, inputs);
    }
}
