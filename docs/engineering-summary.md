# Engineering summary

## What was built

An **agentic SDLC orchestration platform** (Java 21, Spring Boot 3.3.5, Maven) and the **reference URL shortener** it manages.

* **Planner** builds an explicit dependency graph per scenario - nodes with a risk level, entry and exit gates, a retry budget and an optional fallback agent - and extends it while the run progresses.
* **Run engine**: one coordinator, one virtual-thread worker per ready node. Independent branches run in parallel; joins wait for all dependencies.
* **Nine agents**: requirements, architect, developer (+ deterministic fallback developer), test, security, docs, release, reviewer, scope planner. Each writes only through a `GuardedWorkspace` that enforces its autonomy boundary, the secret scan and the dependency allow-list.
* **Reliability**: per-attempt change journal for parallel-safe rollback, bounded retries with exponential backoff and feedback, fallback agent, safe-stop with restore to the last git checkpoint, discard of results computed from superseded inputs.
* **Human oversight**: HIGH-risk nodes and contract/schema changes pause for approval over REST; clarification questions for ambiguous requirements; every human action is attributed and audited.
* **Traceability**: hash-chained audit log, versioned hashed artefacts, decision records with input/output hashes, lineage API, Markdown report per run, reliability metrics (first-attempt success, retries, rollbacks, fallbacks, safe-stops, re-plans, MTTR, end-to-end latency).
* **Three scenarios + four failure demos** with a command-line runner, REST API and committed sample evidence.

## Why it is shaped this way

* The deliverable is the system that delivers software; the shortener is its work item ([ADR-0001](adr/0001-single-repo-two-projects.md)).
* Guarantees are implemented as readable code with tests rather than delegated to a framework ([ADR-0002](adr/0002-java-only-custom-orchestration.md)).
* Output must be reproducible and reviewable offline, so a verified reference implementation backs the fallback and replay modes ([ADR-0003](adr/0003-reference-implementation-and-replay-mode.md)).
* Plan mutation is single-threaded; workers only propose results ([ADR-0004](adr/0004-coordinator-and-virtual-thread-workers.md)).
* Rollback is per attempt, not repository-wide ([ADR-0005](adr/0005-per-attempt-journal-for-rollback.md)).

## Verification

| What | How | Where to see it |
|---|---|---|
| Orchestrator logic: graph, states, policy, boundaries, workspace, git checkpoints, audit chain, retry policy, impact analysis, code-generator parsing | unit tests | `orchestrator/src/test` |
| Engine behaviour with scripted agents (parallelism, retry, rollback, fallback, safe-stop, approvals, rejection, timeout, stop, re-plan, stale results) | 15 tests | `RunEngineTest` |
| All scenarios with the real agents, planner and policy | 8 tests, 5 sample evidence folders | `ScenarioFlowTest`, `docs/sample-runs/` |
| REST control plane incl. human approval over HTTP | Spring MockMvc | `RunControllerTest` |
| Reference shortener: SSRF validation, code generation, API, expiry, rate limit | JUnit + Spring Boot tests | `target-apps/url-shortener/src/test`, `bundles/.../src/test` |
| Real build and test gates | scenario scripts and CI | `./scripts/run-scenarios.sh`, `.github/workflows/ci.yml` |

Two defects were found by the tests themselves and fixed with regression tests: a listing of the workspace racing with git lock files, and git skipping a restore when a file was rewritten with the same size within one timestamp tick (see ADR-0005).

## Known limitations

See the README for the full list. The ones that matter most: runs are in memory and one at a time; non-Developer agents are template-driven; impact analysis is heuristic; the audit chain is tamper-evident but not anchored externally; the approver is identified by name only.

## What I would do next

1. Persist runs and re-hydrate them from the audit log and git history; run several workspaces in parallel (one folder per run).
2. Let a model draft the design, the test plan and the review - behind the same gates - and add evaluation of those outputs.
3. Authenticate approvers (OIDC) and require two-person approval for release.
4. Anchor the audit head hash in an external system; ship events to a log platform.
5. Replace keyword-based concerns in impact analysis with a call-graph and a schema diff.
6. Run generated code in a container sandbox instead of the host's Maven.
