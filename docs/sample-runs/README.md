# Sample runs

These folders are the evidence of five scenario runs (`greenfield`, `brownfield`, `ambiguous`, `failure-build`, `failure-secret`) produced while developing the orchestrator.

**Read this first.** They were produced with a **stand-in for the Maven step**: the compile and test gates were evaluated by a small fake (`FakeBuildRunner`: "does not compile" when the injected defect is present, "tests pass" when test files exist). Everything else - planner, agents, policy, boundaries, approvals (simulated reviewer), retries, rollback, fallback, safe-stop, re-planning, git checkpoints, audit chain, lineage, metrics - is the real code path. Durations are therefore much shorter than a real run.

Runs with the real Maven gates are produced by `./scripts/run-scenarios.sh <scenario>` and land in `docs/runs/` (not in this folder).

Per run: `report.md` (start here), `events.jsonl` (hash-chained audit log), `graph.json`, `decisions.json`, `metrics.json`, `artifacts/` (every generated document and the OpenAPI contract).
