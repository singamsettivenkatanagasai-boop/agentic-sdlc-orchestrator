package com.agentic.orchestrator.agents;

import java.util.List;
import java.util.Optional;

/** Candidate interpretations of the ambiguous requirement "Make links safer". */
public final class Interpretations {
    private Interpretations() { }

    public record Interpretation(String id, String title, String meaning, String question,
                                 boolean recommended, List<String> tasks, String risk) { }

    public static final List<Interpretation> CATALOG = List.of(
            new Interpretation("malware-blocking", "Block malicious destinations",
                    "Reject or flag URLs that appear on a threat blocklist, at creation and at redirect time.",
                    "Should links to known malicious sites be blocked, and who maintains the blocklist?",
                    true,
                    List.of("Choose a blocklist source and refresh strategy",
                            "Check the target URL on create and on redirect",
                            "Return HTTP 422 with a reason; add an admin override"),
                    "HIGH: false positives block legitimate links; adds an external dependency"),
            new Interpretation("link-expiry", "Expire links automatically",
                    "Links stop working after an optional expiry time, limiting how long a bad link stays live.",
                    "Should links be able to expire, and should expired links answer 410 Gone?",
                    true,
                    List.of("Add an optional expiresAt to create and details",
                            "Enforce expiry on redirect (HTTP 410)",
                            "Add the database migration for expires_at"),
                    "MEDIUM: schema change; clients must handle 410"),
            new Interpretation("abuse-rate-limiting", "Limit abuse per client",
                    "Throttle link creation and redirects per client IP to stop automated abuse.",
                    "Should requests be rate limited per IP, and what limit is acceptable?",
                    true,
                    List.of("Add a per-IP token bucket filter",
                            "Answer HTTP 429 with Retry-After",
                            "Make the limit configurable"),
                    "LOW: in-memory limiter is per instance; needs a shared store when scaled out"),
            new Interpretation("access-control", "Authenticated link management",
                    "Only authenticated owners may delete links or read their statistics.",
                    "Is authentication and per-owner authorisation in scope?",
                    false,
                    List.of("Choose API keys or OAuth", "Add owner to the link model", "Scope delete and stats to the owner"),
                    "HIGH: large scope, changes the whole API surface"));

    public static Optional<Interpretation> find(String id) {
        return CATALOG.stream().filter(i -> i.id().equals(id)).findFirst();
    }
}
