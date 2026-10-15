package com.agentic.orchestrator.api;

import com.agentic.orchestrator.audit.EventLog;
import com.agentic.orchestrator.audit.RunEvent;
import com.agentic.orchestrator.engine.LineageService;
import com.agentic.orchestrator.engine.LineageService.Trace;
import com.agentic.orchestrator.engine.ReportGenerator;
import com.agentic.orchestrator.engine.Run;
import com.agentic.orchestrator.engine.RunManager;
import com.agentic.orchestrator.engine.RunRequest;
import com.agentic.orchestrator.governance.ApprovalService.Decision;
import com.agentic.orchestrator.governance.ApprovalService.Request;
import com.agentic.orchestrator.model.NodeSummary;
import com.agentic.orchestrator.model.RunInfo;
import com.agentic.orchestrator.model.RunOptions.ScheduledChange;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST control plane of the orchestrator: start runs, watch the graph and the audit trail,
 * answer human checkpoints, stop or re-plan a run and trace the lineage of a file.
 */
@RestController
@RequestMapping("/runs")
@Tag(name = "Runs", description = "Start a run, answer the human checkpoints, watch progress and read the evidence")
public class RunController {
    private static final String GREENFIELD = """
            {"scenario": "GREENFIELD", "label": "greenfield",
             "requirement": "Build a URL shortener service that creates short links, redirects visitors to the original URL and reports click statistics."}
            """;
    private static final String CUSTOM = """
            {"scenario": "GREENFIELD", "label": "my-requirement",
             "requirement": "Build a URL shortener that creates short links with optional expiry, redirects visitors and reports click statistics."}
            """;
    private static final String GREENFIELD_AUTO = """
            {"scenario": "GREENFIELD", "label": "greenfield-auto", "autoApprove": true}
            """;
    private static final String BROWNFIELD = """
            {"scenario": "BROWNFIELD", "label": "brownfield",
             "requirement": "Add optional link expiry and per-IP rate limiting to the existing URL shortener without breaking its current API.",
             "change": {"afterNode": "implement", "targetNode": "design",
                        "amendment": "Clients must know when to retry: document the Retry-After header on 429 responses in the API contract."}}
            """;
    private static final String AMBIGUOUS = """
            {"scenario": "AMBIGUOUS", "label": "ambiguous", "requirement": "Make links safer."}
            """;
    private static final String FAILURE = """
            {"scenario": "GREENFIELD", "label": "failure-build", "autoApprove": true, "fault": "BUILD_FAILURE"}
            """;
    private static final String APPROVE = """
            {"approved": true, "approver": "alice", "comment": "looks good"}
            """;
    private static final String REJECT = """
            {"approved": false, "approver": "alice", "comment": "please add pagination to the stats endpoint"}
            """;
    private static final String ANSWER = """
            {"approved": true, "approver": "alice",
             "answers": {"link-expiry": true, "abuse-rate-limiting": true, "malware-blocking": false, "access-control": false}}
            """;
    private static final String REPLAN = """
            {"targetNode": "design", "amendment": "Also document the Retry-After header on 429 responses."}
            """;
    private final RunManager runs;
    private final LineageService lineage = new LineageService();
    private final ReportGenerator reports = new ReportGenerator();

    public RunController(RunManager runs) {
        this.runs = runs;
    }

    /** Body of a human decision. {@code answers} is only used for clarification questions. */
    public record DecisionBody(
            @Schema(description = "true approves (or answers), false rejects", example = "true") boolean approved,
            @Schema(description = "Who decides; recorded in the audit trail", requiredMode = Schema.RequiredMode.REQUIRED,
                    example = "alice") String approver,
            @Schema(description = "Reason; on a rejection it is fed back to the agent", example = "looks good") String comment,
            @Schema(description = "Clarification answers only: interpretation id -> in scope") Map<String, Boolean> answers) { }

    @Operation(summary = "Start a run",
            description = "Starts the agents in the background and returns the run id at once. Only one run can be active. "
                    + "Pick an example; edit 'requirement' to give your own requirement.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = RunRequest.class), examples = {
            @ExampleObject(name = "Greenfield (you approve)", value = GREENFIELD),
            @ExampleObject(name = "Greenfield with your own requirement", value = CUSTOM),
            @ExampleObject(name = "Greenfield (auto-approved)", value = GREENFIELD_AUTO),
            @ExampleObject(name = "Brownfield with a mid-run requirement change", value = BROWNFIELD),
            @ExampleObject(name = "Ambiguous requirement", value = AMBIGUOUS),
            @ExampleObject(name = "Failure demo: code that does not compile", value = FAILURE)}))
    @ApiResponse(responseCode = "202", description = "Run started; use the returned runId in the other calls")
    @ApiResponse(responseCode = "400", description = "Missing or unknown scenario", content = @Content)
    @ApiResponse(responseCode = "409", description = "Another run is still active", content = @Content)
    @PostMapping
    public ResponseEntity<Map<String, Object>> start(@RequestBody RunRequest request) {
        if (request.scenario() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scenario is required (GREENFIELD, BROWNFIELD or AMBIGUOUS)");
        }
        Run run = runs.start(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("runId", run.id());
        body.put("status", run.status());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @Operation(summary = "List all runs", description = "Every run since the service started, with its status and metrics")
    @GetMapping
    public List<RunInfo> list() {
        return runs.all().stream().map(Run::info).toList();
    }

    @Operation(summary = "Status and metrics of a run", description = "metrics.status is RUNNING, COMPLETED or STOPPED")
    @GetMapping("/{id}")
    public RunInfo get(@PathVariable String id) {
        return run(id).info();
    }

    @Operation(summary = "The task graph", description = "Every node with its agent, state, risk, dependencies and attempts")
    @GetMapping("/{id}/graph")
    public List<NodeSummary> graph(@PathVariable String id) {
        return run(id).graph().summaries();
    }

    @Operation(summary = "The audit trail", description = "All events in order; chainVerified is true when the hash chain is intact")
    @GetMapping("/{id}/events")
    public Map<String, Object> events(@PathVariable String id) {
        List<RunEvent> events = run(id).log().events();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chainVerified", EventLog.verify(events));
        body.put("events", events);
        return body;
    }

    /** Human checkpoints that are waiting for a decision. */
    @Operation(summary = "What is waiting for a human",
            description = "Approvals and clarification questions waiting for a decision; empty while the agents are working")
    @GetMapping("/{id}/pending")
    public List<Request> pending(@PathVariable String id) {
        run(id);
        return runs.pending(id);
    }

    @Operation(summary = "Approve, reject or answer a checkpoint",
            description = "nodeId comes from GET /runs/{id}/pending (greenfield: design, release, signoff). "
                    + "A rejection sends the node back for rework with your comment.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = DecisionBody.class), examples = {
            @ExampleObject(name = "Approve", value = APPROVE),
            @ExampleObject(name = "Reject", value = REJECT),
            @ExampleObject(name = "Answer the scope question (ambiguous, node 'interpret')", value = ANSWER)}))
    @ApiResponse(responseCode = "204", description = "Decision delivered; the run continues")
    @ApiResponse(responseCode = "400", description = "approver is missing", content = @Content)
    @ApiResponse(responseCode = "404", description = "Unknown run", content = @Content)
    @ApiResponse(responseCode = "409", description = "That node is not waiting for a decision", content = @Content)
    @PostMapping("/{id}/nodes/{nodeId}/approval")
    public ResponseEntity<Void> decide(@PathVariable String id, @Parameter(example = "design") @PathVariable String nodeId,
                                       @RequestBody DecisionBody body) {
        run(id);
        if (body.approver() == null || body.approver().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "approver is required");
        }
        Decision d = new Decision(body.approved(), "human:" + body.approver(), body.comment(),
                body.answers() == null ? Map.of() : body.answers());
        boolean delivered = runs.decide(id, nodeId, d);
        return delivered ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @Operation(summary = "Stop a run", description = "Operator safe-stop: the workspace is restored to the last good checkpoint")
    @ApiResponse(responseCode = "202", description = "Stopping")
    @ApiResponse(responseCode = "409", description = "The run has already finished", content = @Content)
    @PostMapping("/{id}/stop")
    public ResponseEntity<Void> stop(@PathVariable String id) {
        run(id);
        return runs.stop(id, "stopped through the API")
                ? ResponseEntity.accepted().build() : ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    /** Dynamic re-planning: submit a requirement change for a node of a running run. */
    @Operation(summary = "Change the requirement of a running run",
            description = "The target node and everything downstream are marked stale and re-run with the amended requirement")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = ScheduledChange.class),
            examples = @ExampleObject(name = "Amend the design", value = REPLAN)))
    @ApiResponse(responseCode = "202", description = "Change accepted")
    @ApiResponse(responseCode = "409", description = "The run is not running or the re-plan budget is used up", content = @Content)
    @PostMapping("/{id}/replan")
    public ResponseEntity<Void> replan(@PathVariable String id, @RequestBody ScheduledChange change) {
        run(id);
        return runs.requestChange(id, change)
                ? ResponseEntity.accepted().build() : ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @Operation(summary = "Where a file came from", description = "The node, decisions and input files that produced a file of the run")
    @GetMapping("/{id}/lineage")
    public Trace lineage(@PathVariable String id, @Parameter(example = "docs/design.md") @RequestParam String path) {
        Trace t = lineage.trace(run(id).context(), path);
        if (t == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no artifact " + path);
        }
        return t;
    }

    @Operation(summary = "The run report (Markdown)", description = "Outcome, requirement, metrics, graph, timeline and decisions")
    @GetMapping(value = "/{id}/report", produces = "text/markdown")
    public String report(@PathVariable String id) {
        return reports.render(run(id));
    }

    private Run run(String id) {
        return runs.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown run " + id));
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        p.setTitle("Conflict");
        return p;
    }
}
