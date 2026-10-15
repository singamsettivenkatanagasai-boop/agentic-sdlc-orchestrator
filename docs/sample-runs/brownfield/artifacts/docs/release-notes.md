# Release notes

Scenario: BROWNFIELD

## Readiness checklist

| Check | Result | Evidence |
| --- | --- | --- |
| All upstream nodes completed with passing gates | PASS | all done |
| Tests pass | PASS | 31 tests, 0 failures, 0 errors |
| Security review verdict is PASS | PASS | PASS |
| High-impact steps approved by a human | PASS | all approved |
| Rollback plan available | PASS | workspace checkpoint 312cb2647ff9191348f23934c11a4ca2d202c89d |

## Rollback plan

Restore the workspace to checkpoint `312cb2647ff9191348f23934c11a4ca2d202c89d` (`git reset --hard <checkpoint>` inside the workspace) or revert the merge commit.

## Known limitations

- No authentication
- Rate limiting is per instance
- In-memory database in the prototype
