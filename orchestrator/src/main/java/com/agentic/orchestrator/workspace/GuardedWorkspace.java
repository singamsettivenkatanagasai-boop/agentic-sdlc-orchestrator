package com.agentic.orchestrator.workspace;

import com.agentic.orchestrator.governance.AutonomyBoundary;
import com.agentic.orchestrator.governance.PolicyDecision;
import com.agentic.orchestrator.governance.PolicyEngine;
import com.agentic.orchestrator.governance.PolicyOutcome;
import com.agentic.orchestrator.model.PolicyViolationException;
import com.agentic.orchestrator.util.PathSafety;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The view of the workspace handed to ONE agent during ONE attempt.
 *
 * <p>Autonomy is enforced here, in code, not in prompts: every write is checked against the
 * agent's {@link AutonomyBoundary} (allowed paths, max files) and the {@link PolicyEngine}
 * (secrets, dependency allowlist). It also keeps a change journal of the attempt, so the
 * orchestrator can roll back exactly what this attempt touched. A per-attempt journal (rather
 * than a global reset) keeps rollback safe while other nodes run in parallel in the same workspace.
 */
public final class GuardedWorkspace implements Workspace {
    private final Workspace delegate;
    private final String agent;
    private final AutonomyBoundary boundary;
    private final PolicyEngine policy;

    private final Map<String, Optional<String>> journal = new LinkedHashMap<>();  // path -> content before
    private final Set<String> written = new LinkedHashSet<>();

    public GuardedWorkspace(Workspace delegate, String agent, AutonomyBoundary boundary, PolicyEngine policy) {
        this.delegate = delegate;
        this.agent = agent;
        this.boundary = boundary;
        this.policy = policy;
    }

    /** Paths this attempt created or modified (and that still exist). */
    public synchronized Set<String> changedPaths() { return new LinkedHashSet<>(written); }

    /** Undoes every change of this attempt, newest first. */
    public synchronized void rollback() {
        List<String> paths = new ArrayList<>(journal.keySet());
        for (int i = paths.size() - 1; i >= 0; i--) {
            String p = paths.get(i);
            Optional<String> before = journal.get(p);
            if (before.isPresent()) {
                delegate.write(p, before.get());
            } else {
                delegate.delete(p);
            }
        }
        journal.clear();
        written.clear();
    }

    @Override
    public synchronized void write(String rawPath, String content) {
        String path = PathSafety.normalize(rawPath);
        checkBoundary(path);
        PolicyDecision d = policy.checkWrite(agent, path, content);
        if (d.outcome() == PolicyOutcome.DENY) {
            throw new PolicyViolationException(d.rule(), d.reason(), d.fatal());
        }
        remember(path);
        delegate.write(path, content);
        written.add(path);
    }

    @Override
    public synchronized void delete(String rawPath) {
        String path = PathSafety.normalize(rawPath);
        checkBoundary(path);
        remember(path);
        delegate.delete(path);
        written.remove(path);
    }

    private void checkBoundary(String path) {
        if (!boundary.allows(path)) {
            throw new PolicyViolationException("autonomy-boundary", "agent '" + agent
                    + "' may not write '" + path + "' (allowed: " + boundary.writeGlobs() + ")", false);
        }
        if (!written.contains(path) && written.size() >= boundary.maxFiles()) {
            throw new PolicyViolationException("autonomy-boundary", "agent '" + agent
                    + "' exceeded its limit of " + boundary.maxFiles() + " changed files", false);
        }
    }

    private void remember(String path) {
        if (!journal.containsKey(path)) {
            journal.put(path, delegate.exists(path) ? Optional.of(delegate.read(path)) : Optional.empty());
        }
    }

    @Override public Path root() { return delegate.root(); }
    @Override public boolean exists(String path) { return delegate.exists(path); }
    @Override public String read(String path) { return delegate.read(path); }
    @Override public List<String> list(String glob) { return delegate.list(glob); }
    @Override public BuildResult build() { return delegate.build(); }
    @Override public TestResult test() { return delegate.test(); }

    @Override
    public String checkpoint(String label) {
        throw new UnsupportedOperationException("agents cannot create checkpoints");
    }

    @Override
    public void restore(String checkpointId) {
        throw new UnsupportedOperationException("agents cannot restore checkpoints");
    }
}
