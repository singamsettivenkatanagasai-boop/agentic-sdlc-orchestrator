package com.agentic.orchestrator.agents;

import com.agentic.orchestrator.agents.Interpretations.Interpretation;
import com.agentic.orchestrator.context.RunContext;
import com.agentic.orchestrator.model.Scenario;
import com.agentic.orchestrator.util.Md;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Requirement understanding: interprets intent, surfaces ambiguity, records assumptions and
 * normalises the request into an engineering problem with acceptance criteria.
 * Mode {@code interpret} is used for vague requirements: it lists candidate interpretations and
 * lets a human decide the scope.
 */
public class RequirementsAgent implements Agent {
    @Override
    public String name() { return "requirements"; }

    @Override
    public AgentResult execute(AgentTask t) {
        return "interpret".equals(t.node().param("mode", "analyze")) ? interpret(t) : analyze(t);
    }

    private AgentResult analyze(AgentTask t) {
        RunContext c = t.context();
        String low = c.requirement().toLowerCase(Locale.ROOT);
        boolean brownfield = c.scenario() == Scenario.BROWNFIELD;
        boolean expiry = low.contains("expir") || low.contains("ttl");
        boolean rate = low.contains("rate limit") || low.contains("rate-limit") || low.contains("throttl");

        List<String> functional = new ArrayList<>();
        List<String> acceptance = new ArrayList<>();
        if (!brownfield) {
            functional.add("FR-1 Create a short link from a long http(s) URL, with an optional custom alias; creating the same URL again returns the same link");
            functional.add("FR-2 Redirect a short code to its target with HTTP 302 and record a click (timestamp, referrer, user agent)");
            functional.add("FR-3 Return link details and soft-delete a link");
            functional.add("FR-4 Report click statistics: total clicks, clicks per day, top referrers");
            acceptance.add("AC-1 Given a valid URL, when POST /api/v1/links is called, then 201 and a 7-character code are returned");
            acceptance.add("AC-2 Given an existing code, when GET /{code} is called, then 302 with the original URL in Location and one click is recorded");
            acceptance.add("AC-3 Given an alias already used for another URL, when creating a link, then 409 is returned");
            acceptance.add("AC-4 Given a URL that points to localhost or a private address, when creating a link, then 400 is returned (SSRF protection)");
        } else {
            functional.add("FR-0 All existing endpoints keep their current contract (no regression)");
            acceptance.add("AC-0 Given the existing test suite, when the change is applied, then every existing test still passes");
        }
        if (expiry) {
            functional.add("FR-5 A link may carry an optional expiry time; once expired it answers HTTP 410 Gone");
            acceptance.add("AC-5 Given a link whose expiry has passed, when GET /{code} is called, then 410 is returned and no click is recorded");
        }
        if (rate) {
            functional.add("FR-6 Requests are rate limited per client IP; excess requests get HTTP 429 with a Retry-After header");
            acceptance.add("AC-6 Given a client that exceeded its limit, when it calls the API again, then 429 and Retry-After are returned");
        }
        List<String> nonFunctional = List.of(
                "NFR-1 Errors use RFC 7807 problem details",
                "NFR-2 User-supplied URLs are validated (scheme, length, internal addresses)",
                "NFR-3 Health endpoint exposed for monitoring",
                "NFR-4 Behaviour covered by automated unit and integration tests");

        List<String> assumptions = new ArrayList<>();
        assumptions.add("Redirects use HTTP 302 (not 301) so every click reaches the service and can be counted.");
        assumptions.add("Short codes are 7 random base62 characters; collisions are retried up to 5 times.");
        assumptions.add("Storage is in-memory H2 for the prototype; production would use PostgreSQL.");
        assumptions.add("No authentication is in scope: any caller may create and delete links.");
        if (expiry) {
            assumptions.add("Expiry is evaluated at request time in UTC; a missing expiresAt means the link never expires.");
        }
        if (rate) {
            assumptions.add("The rate limit is keyed by remote IP with an in-memory token bucket; behind a proxy, X-Forwarded-For handling is a follow-up.");
        }
        assumptions.forEach(c::addAssumption);

        String problem = brownfield
                ? "Extend the existing URL shortener without breaking its API: " + c.requirement()
                : "Deliver a stateless HTTP service that shortens URLs, redirects visitors and reports click "
                  + "analytics, protected against SSRF and malformed input.";

        StringBuilder doc = new StringBuilder("# Requirements\n\n");
        doc.append("## Original requirement\n\n> ").append(c.requirement()).append("\n\n");
        doc.append("## Normalised problem statement\n\n").append(problem).append("\n\n");
        doc.append("## Functional requirements\n\n").append(Md.bullets(functional)).append('\n');
        doc.append("## Non-functional requirements\n\n").append(Md.bullets(nonFunctional)).append('\n');
        doc.append("## Acceptance criteria\n\n").append(Md.bullets(acceptance)).append('\n');
        doc.append("## Assumptions and resolved ambiguities\n\n").append(Md.bullets(assumptions)).append('\n');
        doc.append("## Out of scope\n\n").append(Md.bullets(List.of(
                "Authentication and authorisation", "Custom domains", "Distributed rate limiting and caching")));
        t.workspace().write("docs/requirements.md", doc.toString());
        return new AgentResult("normalised requirement into " + functional.size() + " functional requirements and "
                + acceptance.size() + " acceptance criteria",
                "kept scope to what the requirement states; recorded " + assumptions.size()
                        + " assumptions explicitly instead of guessing silently");
    }

    private AgentResult interpret(AgentTask t) {
        RunContext c = t.context();
        StringBuilder doc = new StringBuilder("# Interpretations of the requirement\n\n");
        doc.append("> ").append(c.requirement()).append("\n\n");
        doc.append("## Why this is ambiguous\n\n");
        doc.append("\"Safer\" can mean protection from malicious destinations, limited link lifetime, protection from abuse, ")
                .append("or restricted access. Each reading has a different cost, risk and API impact, so the scope must be ")
                .append("chosen by a human before any design or code is produced.\n\n");
        doc.append("## Candidate interpretations\n\n");
        List<List<String>> rows = new ArrayList<>();
        for (Interpretation i : Interpretations.CATALOG) {
            rows.add(List.of(i.id(), i.title(), i.meaning(), i.question(), i.recommended() ? "yes" : "no", i.risk()));
        }
        doc.append(Md.table(List.of("Id", "Interpretation", "Meaning", "Question for the human", "Recommended", "Risk"), rows));
        doc.append("\n## Decision needed\n\nConfirm which interpretations are in scope. Only confirmed ones are planned.\n");
        t.workspace().write("docs/interpretations.md", doc.toString());
        c.putAttribute("interpretations", Interpretations.CATALOG.stream().map(Interpretation::id).toList());
        return new AgentResult("identified " + Interpretations.CATALOG.size() + " candidate interpretations; waiting for a human decision",
                "refused to guess the scope of an ambiguous requirement; asked the human instead");
    }
}
