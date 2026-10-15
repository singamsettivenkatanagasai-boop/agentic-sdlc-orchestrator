# ADR-0001: One repository, two projects

**Status:** accepted

**Context.** The assignment asks for an agentic system that delivers a URL shortener. Those are two different things with different lifecycles: the orchestrator is a platform; the shortener is the product it builds.

**Decision.** One Git repository containing `orchestrator/` and `target-apps/url-shortener/` as separate Maven modules under a thin aggregator pom.

**Consequences.**
* A reviewer clones one link and sees both the platform and the product it manages.
* The orchestrator never depends on shortener code at compile time; it reads the shortener's files as data.
* There is exactly **one working folder** (`workspaces/demo`). Greenfield builds in it from empty; brownfield seeds it with the reference shortener and modifies it. "Greenfield" and "brownfield" are scenario names, not folders.
