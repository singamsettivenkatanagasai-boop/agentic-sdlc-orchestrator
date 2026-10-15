# Agentic SDLC Orchestrator

An agentic software engineering platform that carries a requirement through the
full SDLC (requirements, design, implementation, testing, documentation and
release readiness) under explicit governance.

**What it is:** an orchestration layer built around a dependency graph with
entry and exit gates, parallel paths with joins, human approval checkpoints,
bounded retries, fallback, rollback, safe-stop, policy guardrails, an
audit-grade event log, reliability metrics and dynamic re-planning.

**What it demonstrates it on:** a URL shortener service, built from scratch
(greenfield), enhanced with expiry and rate limiting (brownfield), and driven
from an ambiguous requirement ("make links safer") that triggers clarification.

Agents execute inside defined autonomy boundaries. Humans own approvals and
final quality.
