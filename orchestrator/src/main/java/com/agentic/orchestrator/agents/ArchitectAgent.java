package com.agentic.orchestrator.agents;

import com.agentic.orchestrator.analysis.ImpactAnalyzer;
import com.agentic.orchestrator.analysis.ImpactAnalyzer.ImpactReport;
import com.agentic.orchestrator.context.RunContext;
import com.agentic.orchestrator.model.Scenario;
import com.agentic.orchestrator.util.Md;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Architecture and design. Mode {@code impact} reasons about the existing codebase (brownfield);
 * mode {@code design} produces the design document and the OpenAPI contract, including every
 * requirement amendment received so far (so a re-plan produces a different, re-reviewed design).
 */
public class ArchitectAgent implements Agent {
    private final ImpactAnalyzer analyzer;

    public ArchitectAgent(ImpactAnalyzer analyzer) { this.analyzer = analyzer; }

    @Override
    public String name() { return "architect"; }

    @Override
    public AgentResult execute(AgentTask t) {
        return "impact".equals(t.node().param("mode", "design")) ? impact(t) : design(t);
    }

    private AgentResult impact(AgentTask t) {
        ImpactReport report = analyzer.analyze(t.workspace(), t.context().effectiveRequirement());
        t.workspace().write("docs/impact-analysis.md", report.toMarkdown());
        RunContext c = t.context();
        c.putAttribute("impact.schemaChange", report.schemaChange());
        c.putAttribute("impact.apiChange", report.apiChange());
        c.putAttribute("impact.summary", report.impacted().stream()
                .map(i -> i.component() + ": " + i.reason()).toList());
        return new AgentResult("parsed " + report.model().classes().size() + " classes, "
                + report.model().endpoints().size() + " endpoints; " + report.impacted().size()
                + " impacted components; schema change: " + report.schemaChange(),
                "impact derived from the parsed code structure and the concerns found in the requirement");
    }

    @SuppressWarnings("unchecked")
    private AgentResult design(AgentTask t) {
        RunContext c = t.context();
        String low = c.effectiveRequirement().toLowerCase(Locale.ROOT);
        boolean expiry = low.contains("expir") || low.contains("ttl");
        boolean rate = low.contains("rate limit") || low.contains("rate-limit") || low.contains("throttl");
        boolean brownfield = c.scenario() == Scenario.BROWNFIELD;

        StringBuilder d = new StringBuilder("# Design\n\n");
        d.append("## Goal\n\n").append(c.requirement()).append("\n\n");
        if (!c.amendments().isEmpty()) {
            d.append("## Requirement amendments (design revised)\n\n").append(Md.bullets(c.amendments())).append('\n');
        }
        if (brownfield && c.attribute("impact.summary") instanceof List<?> summary) {
            d.append("## Impact on the existing system\n\n").append(Md.bullets((List<String>) summary)).append('\n');
        }
        d.append("## Components\n\n");
        List<List<String>> comps = new ArrayList<>(List.of(
                List.of("LinkController", "REST API for creating, reading, deleting links and stats"),
                List.of("RedirectController", "GET /{code}: resolves a code and answers 302"),
                List.of("LinkService", "Business rules: idempotent create, alias rules, click recording, stats"),
                List.of("UrlValidator", "SSRF guard: scheme, length, credentials, internal addresses"),
                List.of("CodeGenerator", "Random base62 short codes"),
                List.of("LinkRepository / ClickRepository", "Spring Data JPA persistence"),
                List.of("GlobalExceptionHandler", "RFC 7807 problem responses")));
        if (rate) {
            comps.add(List.of("RateLimitFilter + TokenBucket", "Per-IP throttling in front of all endpoints; HTTP 429 + Retry-After"));
        }
        d.append(Md.table(List.of("Component", "Responsibility"), comps)).append('\n');
        d.append("## Data model\n\n");
        d.append(Md.bullets(List.of(
                "links(id, code UNIQUE (generated or custom alias), long_url, created_at, deleted" + (expiry ? ", expires_at NULL" : "") + ")",
                "clicks(id, link_id, ts, referrer, user_agent)")));
        d.append("\n## Key decisions and trade-offs\n\n");
        d.append(Md.table(List.of("Decision", "Alternative", "Why"), List.of(
                List.of("HTTP 302 redirect", "301 (cacheable)", "301 would let browsers skip the service and hide clicks from analytics"),
                List.of("Random 7-char base62 code", "Sequential id encoded in base62", "Unguessable codes; collisions are cheap to retry. A DB sequence would remove retries at scale"),
                List.of("In-process Caffeine cache for redirects", "Redis", "No extra infrastructure for the prototype; stale entries are bounded by a 60 s TTL"),
                List.of("H2 in-memory", "PostgreSQL", "Zero setup for reviewers; schema is plain JPA so the swap is configuration"))));
        d.append("\n## Risks and mitigations\n\n");
        List<List<String>> risks = new ArrayList<>(List.of(
                List.of("SSRF through shortened URLs", "UrlValidator blocks loopback, private, link-local and credential URLs"),
                List.of("Alias squatting / collisions", "Alias pattern and reserved words; conflict returns 409"),
                List.of("Unbounded click table growth", "Retention policy needed in production (documented limitation)")));
        if (expiry) {
            risks.add(List.of("Cached link outlives its expiry", "Expiry is checked on every resolve against the cached entity"));
        }
        if (rate) {
            risks.add(List.of("Per-instance limiter is bypassable when scaled out", "Move bucket state to a shared store (follow-up)"));
        }
        d.append(Md.table(List.of("Risk", "Mitigation"), risks));
        t.workspace().write("docs/design.md", d.toString());
        t.workspace().write("openapi.yaml", openApi(expiry, rate));
        return new AgentResult("produced design.md and openapi.yaml (" + (expiry ? "with expiry, " : "")
                + (rate ? "with rate limiting, " : "") + c.amendments().size() + " amendments)",
                "contract-first: the OpenAPI file is the reviewed artefact that the implementation must follow");
    }

    static String openApi(boolean expiry, boolean rate) {
        StringBuilder y = new StringBuilder();
        y.append("openapi: 3.0.3\n");
        y.append("info:\n  title: URL Shortener API\n  version: ").append(expiry || rate ? "0.2.0" : "0.1.0")
                .append("\n  description: Create short links, redirect visitors and report click analytics.\n");
        y.append("paths:\n");
        y.append("  /api/v1/links:\n    post:\n      summary: Create a short link (idempotent for the same URL)\n");
        y.append("      requestBody:\n        required: true\n        content:\n          application/json:\n")
                .append("            schema:\n              $ref: '#/components/schemas/CreateLinkRequest'\n");
        y.append("      responses:\n        '201':\n          description: Link created or existing link returned\n")
                .append("          content:\n            application/json:\n              schema:\n")
                .append("                $ref: '#/components/schemas/LinkResponse'\n");
        y.append("        '400':\n          $ref: '#/components/responses/Problem'\n");
        y.append("        '409':\n          $ref: '#/components/responses/Problem'\n");
        if (rate) {
            y.append("        '429':\n          $ref: '#/components/responses/TooManyRequests'\n");
        }
        y.append("  /{code}:\n    get:\n      summary: Redirect to the original URL\n      parameters:\n")
                .append("        - name: code\n          in: path\n          required: true\n          schema:\n            type: string\n");
        y.append("      responses:\n        '302':\n          description: Redirect; Location header holds the target\n");
        y.append("        '404':\n          $ref: '#/components/responses/Problem'\n");
        if (expiry) {
            y.append("        '410':\n          $ref: '#/components/responses/Problem'\n");
        }
        if (rate) {
            y.append("        '429':\n          $ref: '#/components/responses/TooManyRequests'\n");
        }
        y.append("  /api/v1/links/{code}:\n    get:\n      summary: Link details\n      parameters:\n")
                .append("        - name: code\n          in: path\n          required: true\n          schema:\n            type: string\n");
        y.append("      responses:\n        '200':\n          description: Link details\n          content:\n")
                .append("            application/json:\n              schema:\n                $ref: '#/components/schemas/LinkResponse'\n");
        y.append("        '404':\n          $ref: '#/components/responses/Problem'\n");
        if (expiry) {
            y.append("        '410':\n          $ref: '#/components/responses/Problem'\n");
        }
        y.append("    delete:\n      summary: Soft-delete a link\n      parameters:\n")
                .append("        - name: code\n          in: path\n          required: true\n          schema:\n            type: string\n");
        y.append("      responses:\n        '204':\n          description: Deleted\n        '404':\n          $ref: '#/components/responses/Problem'\n");
        y.append("  /api/v1/links/{code}/stats:\n    get:\n      summary: Click statistics\n      parameters:\n")
                .append("        - name: code\n          in: path\n          required: true\n          schema:\n            type: string\n");
        y.append("      responses:\n        '200':\n          description: Total clicks, clicks per day, top referrers\n");
        y.append("        '404':\n          $ref: '#/components/responses/Problem'\n");
        y.append("components:\n  responses:\n    Problem:\n      description: RFC 7807 problem details\n")
                .append("      content:\n        application/problem+json:\n          schema:\n            type: object\n");
        if (rate) {
            y.append("    TooManyRequests:\n      description: Rate limit exceeded\n      headers:\n        Retry-After:\n")
                    .append("          schema:\n            type: integer\n      content:\n        application/problem+json:\n")
                    .append("          schema:\n            type: object\n");
        }
        y.append("  schemas:\n    CreateLinkRequest:\n      type: object\n      required: [url]\n      properties:\n")
                .append("        url:\n          type: string\n          format: uri\n        alias:\n          type: string\n");
        if (expiry) {
            y.append("        expiresAt:\n          type: string\n          format: date-time\n          nullable: true\n");
        }
        y.append("    LinkResponse:\n      type: object\n      properties:\n        code:\n          type: string\n")
                .append("        shortUrl:\n          type: string\n        longUrl:\n          type: string\n")
                .append("        createdAt:\n          type: string\n          format: date-time\n");
        if (expiry) {
            y.append("        expiresAt:\n          type: string\n          format: date-time\n          nullable: true\n");
        }
        return y.toString();
    }
}
