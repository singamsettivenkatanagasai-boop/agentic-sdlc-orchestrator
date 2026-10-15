# ADR-0002: Java only, custom orchestration engine

**Status:** accepted

**Context.** Options were (a) Python orchestration with an agent framework plus a Java shortener, (b) Java orchestration on a workflow engine, (c) a small custom engine in Java.

**Decision.** Everything in Java 21 / Spring Boot with a custom engine.

**Why.**
* One toolchain, one language to review, one CI job; the author's strongest stack.
* The behaviours the assignment grades (gates, bounded retries, rollback, lineage, audit) should be visible code with tests, not configuration of a framework.
* Virtual threads give simple, blocking, debuggable concurrency for parallel branches and human waits.

**Consequences.** No built-in durability or distributed execution (see limitations in the README). Agent frameworks and LLM orchestration libraries are not used; the optional model client is a thin HTTP wrapper.
