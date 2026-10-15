# ADR-0003: Verified reference implementation, fallback agent and replay mode

**Status:** accepted

**Context.** Language models are non-deterministic and can be unavailable; a demo and a review must be repeatable. A system that can only work when a model happens to produce compilable code is not reliable.

**Decision.**
* `target-apps/url-shortener` is a hand-written, tested shortener.
* The `golden-developer` agent copies it and is the **fallback agent** of the `implement` node.
* The `developer` agent replays it when no model is configured, and generates code with a model when `ANTHROPIC_API_KEY` is set.
* The brownfield change (expiry + rate limit) is a verified patch bundle on the classpath.

**Consequences.**
* Runs are deterministic and offline by default; the failure demos use fault injection against that known-good baseline.
* With a model, failures flow back as feedback and the fallback still guarantees a working result.
* Honest caveat: in replay mode the *content* is pre-written. What the system demonstrates is orchestration, governance and recovery, not code synthesis.
