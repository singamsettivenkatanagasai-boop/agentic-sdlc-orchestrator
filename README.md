# Agentic SDLC Orchestrator

**A "virtual software team" that builds an application step by step, while a human approves the important decisions.**

You give it a requirement, for example *"Build a URL shortener service"*. A planner splits the work into steps. Specialised **agents** (an analyst, an architect, a developer, a tester, a security reviewer, a technical writer, a release manager and a reviewer) do the steps in order, some of them in parallel. The run **pauses and waits for a human** before risky decisions. Every action is written to a tamper-evident log. At the end you get a working application and a full report of who did what and why.

> **Contents**
> 1. [What is this, in plain words](#1-what-is-this-in-plain-words)
> 2. [Install what you need (one time)](#2-install-what-you-need-one-time)
> 3. [Start the application](#3-start-the-application)
> 4. [Test it: Swagger (in the browser) or Postman](#4-test-it-swagger-in-the-browser-or-postman)
> 5. [Where to see the results](#5-where-to-see-the-results)
> 6. [If something goes wrong](#6-if-something-goes-wrong)
> 7. [Optional: let Claude write the code](#7-optional-let-claude-write-the-code)
> 8. [For developers and reviewers](#8-for-developers-and-reviewers)

---

## 1. What is this, in plain words

Think of a construction site:

| Construction site | In this project |
|---|---|
| The client asks for a building | You submit a **requirement** |
| The project manager makes a schedule | The **planner** builds a task graph: requirements → design → code → tests → security → docs → release → sign-off |
| Workers (electrician, plumber, ...) | **9 agents**, each allowed to touch only its own area |
| The supervisor redoes a failed job | The **run engine** retries failed steps, undoes bad changes, and switches to a backup worker if needed |
| Safety rules | The **policy engine** blocks leaked passwords, unapproved libraries and writes outside an agent's area |
| The owner signs off on big decisions | **You approve** the design, the release and the final sign-off |
| CCTV and the site logbook | The **audit log**: every step, hash-chained so tampering is detectable |

The application the agents build in the demo is a **URL shortener** (turns a long link into a short one and counts clicks). The shortener is just the example; **the orchestrator, the system that runs the team, is the actual project.**

### The scenarios you can try

| Scenario | What happens | You will be asked to |
|---|---|---|
| **Greenfield** | Builds the URL shortener from an empty folder | approve the design, the release and the sign-off |
| **Brownfield** | Adds link expiry and rate limiting to the *existing* shortener; a requirement change arrives in the middle and the affected steps are redone | approve design and database migration (twice, because of the change), release and sign-off |
| **Ambiguous** | Gets the vague request *"Make links safer."* and, instead of guessing, lists possible meanings | choose which meanings are in scope, then sign off |
| **Failure demos** | The developer agent deliberately makes mistakes: broken code, writing where it is not allowed, leaking a password | nothing; you watch the system recover or stop safely |

> **Good to know:** by default no AI model is called and no internet or API key is needed. The developer agent *replays* a verified implementation (stored in `target-apps/url-shortener`), so every run is repeatable. Everything else (planning, approvals, real build and test checks, retries, audit log) runs for real. See [section 7](#7-optional-let-claude-write-the-code) to let Claude write the code instead.

---

## 2. Install what you need (one time)

You need two free programs, plus Postman if you want the automated test collection. If you already have them, skip ahead.

| Program | Why | Where to get it |
|---|---|---|
| **Java (JDK) 21 or newer** | runs the application | [adoptium.net](https://adoptium.net) → download **Temurin 21 (LTS)** for your system and run the installer |
| **Apache Maven 3.9 or newer** | starts the application and builds the code the agents write | [maven.apache.org/download.cgi](https://maven.apache.org/download.cgi) → *Binary zip archive* |
| **Postman** *(optional)* | runs the ready-made test collection that checks every scenario automatically. Not needed for Swagger, which works in your web browser | [postman.com/downloads](https://www.postman.com/downloads/) |

### Windows: step by step

1. **Java:** run the Temurin installer. On the *Custom Setup* screen, set **"Set JAVA_HOME variable"** and **"Add to PATH"** to *"Will be installed on local hard drive"*. Click through to finish.
2. **Maven:**
   1. Unzip the downloaded file to a simple folder, for example `C:\maven` (so that `C:\maven\bin\mvn.cmd` exists).
   2. Press the **Windows key**, type **environment variables**, open **"Edit environment variables for your account"**.
   3. Select **Path** → **Edit** → **New** → type `C:\maven\bin` → **OK** → **OK**.
3. **Check it worked:** open a **new** Command Prompt or PowerShell window and type:
   ```
   java -version
   mvn -v
   ```
   Both should print a version number. If you see *"not recognized"*, close every terminal window and open a new one, or restart the computer.

### macOS

With [Homebrew](https://brew.sh): `brew install openjdk@21 maven`, then check with `java -version` and `mvn -v`.

### Get the project

* From GitHub: click **Code → Download ZIP**, then unzip it, for example to `C:\projects\agentic-sdlc-orchestrator`.
* Or with git: `git clone <repository url>`.

---

## 3. Start the application

### Option A: one command (easiest)

1. Open the project folder in File Explorer (the folder that contains this `README.md`).
2. Click the address bar, type `powershell` and press **Enter**. A terminal opens in the right folder.
   *(macOS: right-click the folder → New Terminal at Folder.)*
3. Type this and press **Enter**:
   ```
   mvn -pl orchestrator spring-boot:run
   ```
4. The first start downloads libraries and takes a few minutes. It is ready when you see a line ending in:
   ```
   Started OrchestratorApplication in ... seconds
   ```
5. **Leave this window open.** Closing it stops the application.

### Option B: from IntelliJ IDEA

1. **File → Open** → select the project folder → **Trust Project**. Wait until the progress bar at the bottom right finishes.
2. Open `orchestrator/src/main/java/com/agentic/orchestrator/OrchestratorApplication.java`.
3. Click the green ▶ next to `main` → **Run**.
4. If Maven is not installed separately (IntelliJ has its own copy that the application cannot see), open **Run → Edit Configurations → Environment variables** and add
   `MAVEN_COMMAND=<IntelliJ folder>\plugins\maven-plugin\lib\maven3\bin\mvn.cmd`.

### Check that it is running

Open this address in your web browser: **http://localhost:8080/actuator/health**

You should see `{"status":"UP"}`. The application is running and ready for Swagger or Postman.

To **stop** the application: press **Ctrl + C** in the terminal window (Option A) or the red ■ button (Option B).

---

## 4. Test it: Swagger (in the browser) or Postman

There are two ways to talk to the application. Pick one:

| | **Swagger** (section 4.1) | **Postman** (sections 4.2 to 4.4) |
|---|---|---|
| Install anything? | No, it opens in your web browser | Yes, Postman |
| Best for | trying it by hand, seeing every request with ready-made examples | running **all** scenarios automatically with pass/fail checks |
| Address / file | **http://localhost:8080/swagger-ui.html** | `postman/agentic-sdlc-orchestrator.postman_collection.json` |

### 4.1 Swagger: try it in your browser (no install)

1. With the application running, open **http://localhost:8080/swagger-ui.html**.
   You see every operation of the API, each with an explanation and ready-made example bodies.
2. **Start a run:** click **POST /runs** → **Try it out**. In the **Examples** drop-down pick, for example, *Greenfield (you approve)* or *Greenfield with your own requirement*. Change the `requirement` text if you like, then click **Execute**.
   Copy the `runId` from the response (for example `run-1a2b3c4d`).
3. **See what waits for you:** **GET /runs/{id}/pending** → **Try it out** → paste the run id → **Execute**.
   An empty list `[]` means the agents are still working: wait a few seconds and click **Execute** again.
   Otherwise it shows the step that waits for you, e.g. `"nodeId": "design"`.
4. **Approve (or reject):** **POST /runs/{id}/nodes/{nodeId}/approval** → **Try it out** → run id, node id (`design`) → pick the *Approve* or *Reject* example → **Execute**. A `204` response means your decision was delivered.
5. Repeat 3 and 4. Greenfield pauses three times: **design**, **release**, **signoff**.
6. **Check the result:** **GET /runs/{id}** shows `"status": "COMPLETED"` under `metrics` when it is done; **GET /runs/{id}/report** returns the full report.

For programs and tools, the machine-readable API description is at **http://localhost:8080/v3/api-docs**.

### 4.2 Postman: import the collection (one time)

The project includes a ready-made Postman collection that tests every scenario and checks every result for you.

1. Open Postman.
2. Click **Import** (top left) → **files** → select
   `postman/agentic-sdlc-orchestrator.postman_collection.json` from the project folder.
3. A collection named **Agentic SDLC Orchestrator** appears on the left, with folders `0.` to `8.`.

### 4.3 Postman: run all tests automatically

1. Make sure the application is running (section 3).
2. Hover over **Agentic SDLC Orchestrator** → click the **⋯** menu → **Run collection**.
3. Leave the settings as they are and click **Run Agentic SDLC Orchestrator**.
4. Wait about **5 minutes**. Postman starts each scenario, waits whenever the application needs a human, approves (or rejects or answers, depending on the scenario), and checks the outcome.
5. **Reading the result:** every check is listed as **PASS** (green) or **FAIL** (red). All green means the whole system works.

What the folders check:

| Folder | What it proves |
|---|---|
| **0. Smoke** | the application is up and no other run is busy |
| **1. Greenfield** | the app is built from scratch: 9 of 9 steps done, 3 human approvals, audit log intact, report produced and showing the requirement |
| **1b. Your own requirement** | a run started with *your* requirement text (collection variable `requirement`) completes and the report shows your text |
| **2. Reject the design once** | a human rejection sends the design back for rework; it is then approved |
| **3. Brownfield** | existing code is changed safely; a migration step is added during the run; a mid-run requirement change is handled |
| **4. Ambiguous** | the system asks instead of guessing, and plans only what you confirmed |
| **5. Build failure** | broken code is retried, undone, and the backup worker recovers |
| **6. Boundary violation** | an agent writing outside its area is blocked; the run still completes |
| **7. Leaked secret** | a leaked password stops the run safely |
| **8. Operator controls and errors** | a run can be stopped; only one run at a time; wrong requests get clear error codes |

### 4.4 Postman: click through one scenario yourself

This is the best way to *see* the human-approval idea. Open folder **1. Greenfield** and send the requests one by one with the blue **Send** button:

1. **GF · Start run**: starts building. The run id is remembered automatically.
2. **GF · Wait for checkpoint**: asks *"is anything waiting for me?"*. Open the **Console** (bottom left of Postman) to read the answer:
   * *"Still RUNNING ... click Send again"*: the agents are working; wait a few seconds and send again.
   * *"Human needed at 'design'"*: the run is paused for you.
3. **GF · Decide checkpoint**: approves the step that is waiting.
   To decide yourself, open the **Body** tab and replace `{{decisionBody}}` with, for example:
   ```json
   { "approved": false, "approver": "your name", "comment": "please add pagination" }
   ```
   A rejection sends the step back for rework, and it will ask you again.
4. Repeat steps 2 and 3. Greenfield pauses three times: **design**, **release**, **signoff**.
5. When step 2 says *"Run finished"*, send **GF · Verify outcome**, **GF · Audit trail**, **GF · Graph** and **GF · Report** and look at the **Test Results** tab.

### 4.5 Give your own requirement

Every run starts from a **requirement**. Each *Start run* request in the collection shows it in its body, so you can read and change it:

```json
{
  "scenario": "GREENFIELD",
  "label": "my-requirement",
  "requirement": "Build a URL shortener that creates short links with optional expiry, redirects visitors and reports click statistics.",
  "autoApprove": false
}
```

* **Easiest:** in Postman click the collection → **Variables** → change the value of **`requirement`**, then use folder **1b. Your own requirement**.
* **In Swagger:** pick the example *Greenfield with your own requirement* and edit the text.
* **Change it while a run is going:** `POST /runs/<runId>/replan` with `{"targetNode": "design", "amendment": "Also document the Retry-After header."}`. The design and everything after it are redone.

Where your text appears: at the top of the report (`GET /runs/<runId>/report`, line *Requirement*) and in `docs/requirements.md` under *Original requirement*.

> **Be aware:** without an AI model ([section 7](#7-optional-let-claude-write-the-code)) the agents still build the same URL shortener whatever you write; your text is recorded, and the words *expiry* or *rate limit* add matching requirements. With an AI model, the developer agent writes code from your requirement.

### 4.6 Your own requests (optional)

| Purpose | Method and address | Body |
|---|---|---|
| Start a run | `POST http://localhost:8080/runs` | `{"scenario":"GREENFIELD","requirement":"your text"}` (or `BROWNFIELD`, `AMBIGUOUS`; `requirement` is optional) |
| What is waiting for me? | `GET http://localhost:8080/runs/<runId>/pending` | none |
| Approve a step | `POST http://localhost:8080/runs/<runId>/nodes/design/approval` | `{"approved":true,"approver":"your name","comment":"looks good"}` |
| Status and numbers | `GET http://localhost:8080/runs/<runId>` | none |
| Full report | `GET http://localhost:8080/runs/<runId>/report` | none |
| Stop a run | `POST http://localhost:8080/runs/<runId>/stop` | none |

More detail: [docs/postman-testing.md](docs/postman-testing.md).

---

## 5. Where to see the results

After a run, look in the project folder:

| Folder | What is inside |
|---|---|
| `data/runs/<label>/report.md` | **Start here.** The outcome, the numbers (retries, approvals, time taken), a diagram of the steps, and every decision. Open it in VS Code or IntelliJ and use the Markdown preview. Runs from the Postman collection are labelled `postman-greenfield`, `postman-brownfield`, ... |
| `data/runs/<label>/artifacts/docs/` | The documents the agents wrote: requirements, design, test plan, security review, release notes |
| `data/runs/<label>/events.jsonl` | The audit log, one line per event |
| `workspaces/demo/` | The application the agents built in the latest run. It is a normal Java project you can start (see below). |

**Try the URL shortener the agents built** (stop the orchestrator first, since both use port 8080):

```
cd workspaces/demo
mvn spring-boot:run
```

Then in Postman: `POST http://localhost:8080/api/v1/links` with body `{"url":"http://203.0.113.10/hello"}`. You get back a short code; opening `http://localhost:8080/<code>` redirects to the long address.

---

## 6. If something goes wrong

| What you see | What it means | What to do |
|---|---|---|
| `'mvn' is not recognized` / `mvn: command not found` | Maven is not installed or not on the Path | Redo [section 2](#2-install-what-you-need-one-time), then open a **new** terminal |
| Run ends **STOPPED** and the report mentions `mvn.cmd` or *"Is Maven installed"* | The application cannot find Maven to build the generated code | Same as above; in IntelliJ set `MAVEN_COMMAND` (section 3, Option B) |
| `Port 8080 was already in use` | Another copy is already running | Stop the other copy (close its window, or the red ■ in IntelliJ) |
| Postman: **Could not send request** / *ECONNREFUSED*, or the Swagger page does not open | The application is not running | Start it (section 3) and check the health address |
| Postman: Start run returns **409** | A previous run is still busy (only one run at a time) | Wait for it to finish, or send *8. Operator controls → OP · Stop the run* |
| Run ends **STOPPED** with *"being used by another process"* | `workspaces/demo` is open in another program | Close that folder in IntelliJ, VS Code or File Explorer and run again |
| The first start is very slow | Maven is downloading libraries | Normal the first time only |

---

## 7. Optional: let Claude write the code

By default, the developer and test agents replay verified code. To have **Claude** (Anthropic's AI model) write the code instead:

1. Create an API key at [console.anthropic.com](https://console.anthropic.com) (this is separate from a claude.ai chat subscription, and calls are billed).
2. Set the environment variable `ANTHROPIC_API_KEY` before starting the application (in IntelliJ: **Run → Edit Configurations → Environment variables**). Optionally set `ANTHROPIC_MODEL` (default `claude-sonnet-5-5`).
3. Run a scenario as usual. The audit log then says *"generated N files with the language model"* instead of *"replayed the verified implementation"*.

Everything Claude writes goes through the same safety checks (allowed folders, secret scan, allowed libraries) and must compile and pass the tests. If it fails, the error output is sent back to Claude for another attempt; after the retries the backup worker applies the verified code. Never put the key in a file that is committed.

---

## 8. For developers and reviewers

### Architecture

The work item is a URL shortener, as the assignment asks. The shortener is what the agents *produce*; the **orchestrator is the deliverable**. The repository has two projects: `orchestrator/` (the platform) and `target-apps/url-shortener/` (a hand-written, tested reference shortener that is the fallback implementation, the brownfield baseline and the source for resets). See [ADR-0001](docs/adr/0001-single-repo-two-projects.md) and [ADR-0003](docs/adr/0003-reference-implementation-and-replay-mode.md).

```
requirement ─► Planner ─► dependency graph (nodes, entry/exit gates, risk)
                              │
      ┌───────────────────────▼─────────────────────────────────────────┐
      │  Run engine (coordinator + virtual-thread workers)              │
      │  retry · rollback · fallback agent · safe-stop · re-plan        │
      └──────┬──────────────┬──────────────┬──────────────┬─────────────┘
             │              │              │              │
        Agents (9)     Policy engine   Approval      Audit log (hash chain)
        read all,      secrets, deps,  service       Decision lineage
        write only     boundaries,     (humans via   Metrics
        inside their   change control  REST API)
        boundary
```

More: [docs/architecture.md](docs/architecture.md) and the decision records in [docs/adr/](docs/adr/).

### What it does, requirement by requirement

| Assignment capability | Where it lives | Evidence |
|---|---|---|
| Explicit dependency graph with entry and exit gates | `planner/Planner`, `graph/` (`TaskNode`, `Gates`, `WorkflowGraph`) | `report.md` of every run shows the graph (Mermaid) and the gate verdicts |
| Parallel branches and joins | Ready nodes run on virtual threads; a join is a node whose dependencies are all `DONE` | design ∥ test-plan, security ∥ docs; `RunEngineTest.independentBranches...` |
| Cross-stage context and decision lineage | `context/RunContext` (hashed, versioned artefacts), `DecisionRecord`, `engine/LineageService`, `GET /runs/{id}/lineage` | `decisions.json`, lineage test |
| Human approval checkpoints | `governance/ApprovalService`; HIGH-risk nodes and contract/schema changes pause the run until a human decides over REST | `RunControllerTest`, `rejection` scenario |
| Bounded retries, fallback, rollback, safe-stop | `engine/RunEngine` (per-attempt change journal, exponential backoff, fallback agent, safe-stop with workspace restore) | `failure-build`, `failure-boundary`, `failure-secret` scenarios |
| Policy guardrails: security, compliance, change control | `governance/PolicyEngine`, `AutonomyBoundaries`, enforced inside `GuardedWorkspace` (agents cannot bypass them) | `PolicyEngineTest`, `GuardedWorkspaceTest` |
| Audit-grade event trail | `audit/EventLog`: append-only, hash-chained, JSONL, fail-closed | `EventLogTest` (tamper detection), `events.jsonl` per run |
| Reliability metrics | `engine/MetricsCollector`: first-attempt success rate, retries, rollbacks, fallbacks, safe-stops, re-plans, approvals, MTTR, end-to-end latency; also Micrometer (`/actuator/metrics/agentic.*`) | metrics table in every `report.md` |
| Dynamic re-planning | Graph grows at run time (migration node, per-interpretation plan nodes); requirement changes invalidate the affected sub-graph | `brownfield` scenario, `RunEngineTest.requirementChange...` |
| Three scenarios | greenfield, brownfield, ambiguous (+ failure demos) | [scenarios/](scenarios/) and [docs/sample-runs/](docs/sample-runs/) |

### Command line

```bash
mvn -B verify                                   # build and run all unit, engine, scenario and API tests

# run scenarios end to end with a simulated human answering the approvals; evidence goes to docs/runs/<scenario>/
./scripts/run-scenarios.sh greenfield           # Windows: .\scripts\run-scenarios.ps1 greenfield
./scripts/run-scenarios.sh all                  # greenfield, brownfield, ambiguous, failure-build, failure-boundary, failure-secret, rejection

./scripts/start-server.sh                       # the REST service on http://localhost:8080
```

### REST API

| Endpoint | Purpose |
|---|---|
| `POST /runs` | start a run (`scenario` required; optional `label`, `requirement`, `autoApprove`, `rejectFirstNode`, `fault`, `change`, `clarificationAnswers`, `approvalTimeoutSeconds`) |
| `GET /runs`, `GET /runs/{id}` | all runs; status and metrics of one run |
| `GET /runs/{id}/graph`, `/events`, `/report` | the live graph, the audit trail (with chain verification), the Markdown report |
| `GET /runs/{id}/pending` | approvals and clarifications waiting for a human |
| `POST /runs/{id}/nodes/{node}/approval` | approve, reject, or answer a clarification (`answers`) |
| `POST /runs/{id}/stop`, `/replan` | operator safe-stop; submit a requirement change for a node |
| `GET /runs/{id}/lineage?path=docs/design.md` | which decisions and inputs produced a file |

There is one working folder (`workspaces/demo`), so one run is active at a time; a second `POST /runs` returns 409.

Interactive documentation: **Swagger UI** at `/swagger-ui.html` (springdoc-openapi, with example bodies for every scenario) and the OpenAPI 3 description at `/v3/api-docs`. The descriptions live in annotations on `api/RunController`, `engine/RunRequest` and `model/RunOptions.ScheduledChange`; the page header in `config/OpenApiConfig`.

Configuration (`application.properties`, overridable as `--agentic.<name>=...` or environment variables): `workspace-dir`, `export-dir` (default `data/runs` for the service, `docs/runs` for the scenario runner), `maven-offline`, `maven-timeout-minutes`, `maven-command` (`MAVEN_COMMAND`), `llm.api-key` (`ANTHROPIC_API_KEY`), `llm.model` (`ANTHROPIC_MODEL`).

### Testing approach

| Layer | What | How |
|---|---|---|
| Unit | graph and state machine, policy, guarded workspace, event log, retry policy, code generator parsing, impact analyzer | JUnit 5, no mocks needed |
| Engine | parallelism and joins, retry with rollback and feedback, fallback, exhaustion → safe-stop, fatal policy violation, approval pause/resume, rejection, timeout, operator stop, re-planning, discarded stale results, replan budget | scripted agents, real engine, real git checkpoints |
| Scenario | all scenarios end to end with the real agents, planner and policy | build/test step replaced by a fast stand-in (`FakeBuildRunner`) so the suite runs offline in seconds |
| API | REST control plane incl. human approval over HTTP | Spring `MockMvc` |
| API, end to end | every scenario, human decisions, failure handling and error responses against a running server with real Maven gates | [Postman collection](postman/agentic-sdlc-orchestrator.postman_collection.json), see [docs/postman-testing.md](docs/postman-testing.md) |
| Reference app | URL validation (SSRF), code generation, HTTP API end to end, expiry, rate limit | JUnit 5 + Spring Boot test; the brownfield tests ship with the patch and run when the agents build the change |

The scenario scripts use the **real Maven** build and test gates. CI (`.github/workflows/ci.yml`) runs `mvn verify` on every push and offers a manual job that runs the scenarios.

### Key design decisions and trade-offs

* **Custom orchestration engine instead of a framework.** A few thousand lines of plain Java make every guarantee (gates, rollback, lineage, audit) explicit and testable. A workflow engine (Temporal, Camunda) would add durability but hide the behaviours this assignment asks to see. [ADR-0002](docs/adr/0002-java-only-custom-orchestration.md)
* **Coordinator + workers.** Workers run a node's reliability loop; only the single coordinator thread accepts results, expands the plan and re-plans. That removes races between "node finished" and "plan changed". [ADR-0004](docs/adr/0004-coordinator-and-virtual-thread-workers.md)
* **Per-attempt change journal, not `git reset`, for rollback.** Parallel nodes write to the same folder; resetting the whole repository would destroy a sibling's work. Git checkpoints are used for durable history and for restoring the last good state on safe-stop. [ADR-0005](docs/adr/0005-per-attempt-journal-for-rollback.md)
* **Boundaries are enforced in code, not in prompts.** An agent can only write through `GuardedWorkspace`, which checks path globs, file counts, secrets and dependency allow-lists on every write.
* **Fail closed.** If the audit log cannot be written the run stops; a path escape or a secret is a fatal violation (no retries).
* **Deterministic replay by default.** The system can be reviewed and demonstrated without network access or an API key; the model-backed path is opt-in.

### Limitations (honest list)

* **One run at a time** (a single working folder), and run state is in memory: restarting the service loses the live state (the exported evidence and the git history survive). Production would persist runs and use a queue.
* **Without an API key no code is generated:** the Developer and Test agents replay the reference implementation.
* **Agents other than Developer/Test are template-driven**, not model-driven. They derive their content from the requirement, the parsed code and recorded facts, which keeps them deterministic and auditable, but they do not reason about arbitrary requirements. Greenfield always produces the URL shortener.
* **Impact analysis is heuristic** (structure from JavaParser, concerns from keywords). It handles expiry, rate limiting and a few other concerns, not arbitrary changes; in brownfield replay mode the expiry + rate-limit change set is applied whatever the requirement says.
* **Gates check that the code builds and its tests pass**, not that it implements the requested feature.
* **Secret scanning is pattern based** and the dependency allow-list is by `groupId`.
* **The audit chain is tamper-*evident*, not tamper-proof**: someone with write access to the file can rewrite the whole chain. Anchoring the head hash externally would close that.
* **Reference shortener**: in-memory H2, per-instance cache and rate limit, DNS-rebinding is not re-checked at redirect time, no authentication. Documented in its design and security review.
* **Approvals** are identified by a name in the request; there is no authentication of the approver.

### Repository layout

```
orchestrator/                 the platform (Java 21, Spring Boot 3.3.5)
  src/main/java/.../
    planner/    graph construction and run-time expansion
    graph/      nodes, states, gates
    engine/     coordinator, workers, retry, replan, metrics, export, lineage
    agents/     the nine SDLC agents + deterministic fallback
    governance/ policy engine, autonomy boundaries, approvals
    workspace/  guarded workspace, git checkpoints, Maven runner
    audit/      hash-chained event log
    context/    shared run context and decision records
    analysis/   JavaParser impact analysis
    llm/        optional model client (Anthropic Messages API)
    api/ config/ scenario/   REST, wiring, command-line scenario runner
  src/main/resources/bundles/   the verified expiry + rate-limit change set (brownfield replay)
target-apps/url-shortener/    reference shortener: fallback, brownfield baseline, reset source
postman/      Postman collection: end-to-end API tests of every scenario
docs/         architecture.md · adr/ · postman-testing.md · sample-runs/ (stand-in build) · runs/ (scenario-script runs) · engineering-summary.md
scenarios/    walkthrough of each scenario
scripts/      run-scenarios, start-server
data/runs/    evidence of runs started through the REST API (created on first run)
workspaces/demo/   the working folder the agents build in (created on first run)
```
