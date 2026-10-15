#!/usr/bin/env sh
# Runs one demo scenario (or all) with a simulated human answering the approval checkpoints.
# Evidence (audit log, graph, decisions, metrics, report, generated documents) is written to docs/runs/<scenario>.
#
#   ./scripts/run-scenarios.sh [greenfield|brownfield|ambiguous|failure-build|failure-boundary|failure-secret|rejection|all]
#
# Needs JDK 21 and Maven on the PATH. The first run downloads the generated project's dependencies.
set -eu
SCENARIO="${1:-all}"
cd "$(dirname "$0")/.."
exec mvn -B -q -pl orchestrator spring-boot:run \
  -Dspring-boot.run.profiles=scenario \
  -Dspring-boot.run.arguments="--agentic.scenario=${SCENARIO}"
