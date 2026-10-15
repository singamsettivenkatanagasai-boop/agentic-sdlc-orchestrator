# Failure and recovery demos

All four use the greenfield graph and inject a fault into the Developer agent (or script the human). They show that autonomy is **bounded** and failure is **contained**.

| Scenario | Injected fault | What the system does | Outcome |
|---|---|---|---|
| `failure-build` | developer emits a class that does not compile | build gate fails → attempt rolled back → retry with the compiler error as feedback (backoff 200 ms, 400 ms) → third failure → **fallback agent** `golden-developer` → build passes | COMPLETED; retries 2, rollbacks 3, fallbacks 1, MTTR measured |
| `failure-boundary` | developer tries to write `.github/workflows/deploy.yml` | autonomy boundary denies the write (non-fatal) → rollback → same on each retry → fallback agent recovers; the file never exists | COMPLETED; fallbacks 1 |
| `failure-secret` | developer writes a file containing an AWS-style key | policy engine denies the write as **fatal**: the content never reaches the disk, no retry, **safe-stop**: all unfinished nodes `STOPPED`, workspace restored to the last checkpoint; approved design work is kept | **STOPPED**; safe-stops 1 |
| `rejection` | the human rejects the design once | `AWAITING_APPROVAL → ROLLED_BACK`, the design is reworked with the reviewer's comment, approved the second time | COMPLETED; design attempts 2 |

Run: `./scripts/run-scenarios.sh failure-build` (and the others), or `all`.

What to look at in `report.md`: the **Timeline** table (`GATE` failed, `ROLLBACK`, `RETRY`, `FALLBACK`, `SAFE_STOP`), the **Reliability metrics** table, and the node table (`STOPPED` vs `DONE`).
