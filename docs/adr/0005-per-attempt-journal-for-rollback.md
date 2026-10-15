# ADR-0005: Per-attempt change journal for rollback, git for checkpoints

**Status:** accepted

**Context.** Nodes run in parallel in the same folder. `git reset --hard` after a failure would also remove files a sibling node is legitimately writing.

**Decision.** Every attempt gets its own `GuardedWorkspace` which journals the previous content of each path it touches. Rolling back an attempt restores exactly those paths. Git commits are made by the coordinator after each accepted node and at run start; they provide durable history and the restore point for safe-stop.

**Consequences.**
* Parallel-safe rollback.
* A checkpoint commit can include files of a node that is still running; safe-stop restores to the last commit and the in-flight workers roll back their own journals, which makes the final state consistent. Accepted as a documented trade-off.
* Restore deletes the git index first, because git's size+timestamp shortcut can skip a file rewritten with the same size within one timestamp tick (found by a flaky test).
