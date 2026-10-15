# Release notes

Scenario: GREENFIELD

## Readiness checklist

| Check | Result | Evidence |
| --- | --- | --- |
| All upstream nodes completed with passing gates | PASS | all done |
| Tests pass | PASS | 20 tests, 0 failures, 0 errors |
| Security review verdict is PASS | PASS | PASS |
| High-impact steps approved by a human | PASS | all approved |
| Rollback plan available | PASS | workspace checkpoint 619fd526c0165a7761f4b5665d7fef700bec3534 |

## Rollback plan

Restore the workspace to checkpoint `619fd526c0165a7761f4b5665d7fef700bec3534` (`git reset --hard <checkpoint>` inside the workspace) or revert the merge commit.

## Known limitations

- No authentication
- Rate limiting is per instance
- In-memory database in the prototype
