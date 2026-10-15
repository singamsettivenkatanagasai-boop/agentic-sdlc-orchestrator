# Testing the orchestrator with Postman

Everything the scenario scripts do can be tested through the REST API with the Postman collection in
[`postman/agentic-sdlc-orchestrator.postman_collection.json`](../postman/agentic-sdlc-orchestrator.postman_collection.json).
No scripts, no command line: start the server, import the collection, run it.

Prefer the browser? The same API is documented and executable in **Swagger UI** at `http://localhost:8080/swagger-ui.html`,
with example bodies for every scenario (see the main [README](../README.md#41-swagger-try-it-in-your-browser-no-install)).

## 1. Start the server

Prerequisites: JDK 21+ and Maven 3.9+ on the `PATH` (the orchestrator runs real `mvn compile` / `mvn test` on the code the agents produce).

* **IntelliJ:** run `OrchestratorApplication` with **no** active profile and **no** program arguments.
  If Maven is not on the `PATH`, add the environment variable `MAVEN_COMMAND=<full path to mvn.cmd>` to the run configuration.
* **Terminal:** `mvn -pl orchestrator spring-boot:run`

Wait for `Started OrchestratorApplication`. The API is at `http://localhost:8080`.

## 2. Import the collection

Postman → **Import** → select `postman/agentic-sdlc-orchestrator.postman_collection.json`.
The collection variables hold the settings; change them under the collection's **Variables** tab:

| Variable | Default | Meaning |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | where the server runs |
| `approver` | `tester` | name recorded in the audit trail for your decisions |
| `pollDelayMs` | `2000` | wait between status polls |
| `maxPolls` | `300` | give up after this many polls (10 minutes) |
| `requirement` | a URL-shortener requirement with expiry | the requirement used by folder *1b. Your own requirement*; change it to submit your own |

The other variables (`runId`, `nodeId`, ...) are filled in by the scripts.

## 3a. Run everything automatically (recommended first)

Right-click the collection → **Run collection** → **Run**. The runner starts every scenario, waits for each
human checkpoint, makes the decision, waits for the run to finish and checks the outcome. Expect about 5 minutes
(real Maven builds). Every check shows as a green or red test result.

## 3b. Or click through a scenario by hand

Each scenario folder has the same shape:

| Step | Request | What to do |
|---|---|---|
| 1 | **Start run** | Send. The run id is saved automatically. |
| 2 | **Wait for checkpoint** | Send. The **Console** (bottom left) says either *Human needed at 'design' ...* or *Still RUNNING, click Send again*. |
| 3 | **Decide checkpoint** | Send. It approves the checkpoint found in step 2 (or rejects / answers, depending on the folder). |
| 4 | repeat 2 and 3 | until step 2 says *Run finished*. |
| 5 | **Verify outcome**, **Audit trail**, ... | Send. Read the **Test Results** tab. |

To make a decision yourself instead, edit the body of **Decide checkpoint**, e.g.
`{"approved": false, "approver": "sai", "comment": "please add pagination"}`.

## What each folder proves

| Folder | Scenario | Human checkpoints | Expected result |
|---|---|---|---|
| 0. Smoke | server health, no run active | - | `UP`, nothing `RUNNING` |
| 1. Greenfield | build the shortener from an empty folder | design, release, signoff | `COMPLETED`, 9/9 nodes, 3 approvals, design ∥ test-plan, audit chain verified, lineage of `docs/design.md`, report |
| 1b. Your own requirement | greenfield started with the `requirement` collection variable | design, release, signoff | `COMPLETED`, the report shows the submitted requirement |
| 2. Reject design once | you reject the design with a comment | design (rejected), design, release, signoff | `COMPLETED`, design ran twice, 1 rollback |
| 3. Brownfield | add expiry + rate limit to the existing code; a requirement change arrives after `implement` | design, migration, design, migration, release, signoff | `COMPLETED`, `impact` and run-time `migration` node, 1 re-plan, 11/11 nodes |
| 4. Ambiguous | "Make links safer." | interpret (you pick the scope), signoff | `COMPLETED`, plan nodes only for `link-expiry` and `abuse-rate-limiting` |
| 5. Build failure | developer writes code that does not compile | auto-approved | `COMPLETED` after retries, rollbacks and the fallback agent |
| 6. Boundary violation | developer writes `.github/workflows/deploy.yml` | auto-approved | write denied, `COMPLETED` via fallback |
| 7. Leaked secret | developer writes an AWS key | auto-approved | `STOPPED` by safe-stop, no retries |
| 8. Operator controls and errors | stop a run, one-run-at-a-time, bad input | - | 409 for a second run, `STOPPED` after stop, 400/404/409 for invalid requests |

Folders 5-7 use `"autoApprove": true` so the failure handling is tested without clicking.

Every *Start run* body states its `requirement` explicitly (the scenario's default text), so you can read and edit what each run is asked to build.
Without `ANTHROPIC_API_KEY` the code built is the reference URL shortener whatever the requirement says; the text is recorded in
`docs/requirements.md` and the report, and the keywords *expiry* / *rate limit* add requirements FR-5 / FR-6.

## Where to look besides Postman

* **Evidence per run:** `data/runs/<label>/` (`report.md`, `events.jsonl`, `graph.json`, `decisions.json`, `metrics.json`, generated documents). Labels are `postman-greenfield`, `postman-brownfield`, ...
* **Generated code:** `workspaces/demo/` (overwritten by every run). `git log --oneline` there shows one commit per step.

## Troubleshooting

| Symptom | Cause |
|---|---|
| Start run returns **409** | another run is still active. Stop it with *8. Operator controls → Stop the run* (set `runId`) or wait. |
| Run ends `STOPPED`, report mentions `mvn` / `mvn.cmd` | Maven is not on the `PATH` of the server process; set `MAVEN_COMMAND`. |
| Run ends `STOPPED` with "being used by another process" | `workspaces/demo` is open in another program (e.g. IntelliJ); close it. |
| Wait for checkpoint keeps saying *Still RUNNING* | normal during builds; with the runner it polls by itself. |

## Verified

The collection's flow was executed against a live server (all folders, real Maven gates); every check passed.
Running the collection surfaced one defect, now fixed: `POST /runs` without a `scenario` returned 500 instead of 400.
