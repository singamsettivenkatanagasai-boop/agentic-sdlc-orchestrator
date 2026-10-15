# ADR-0004: Single coordinator, virtual-thread workers

**Status:** accepted

**Context.** Independent nodes must run in parallel, humans can take minutes, and the plan can change while nodes are running.

**Decision.** One coordinator loop owns graph mutation. Each ready node runs on a virtual thread that performs the node's reliability loop (attempts, rollback, backoff, fallback, approval wait) and returns a `Completion`. Workers never mark a node `DONE`; the coordinator accepts or discards the result.

**Consequences.**
* No locks around plan changes; "result arrives after re-plan" has a single, tested code path (the *invalidated* set).
* The coordinator polls completions every 200 ms, which bounds reaction time to operator stop and re-plan requests.
* A worker blocked on a human holds a cheap virtual thread, not a platform thread.
