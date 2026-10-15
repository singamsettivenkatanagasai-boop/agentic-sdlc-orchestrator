package com.agentic.shortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request and response bodies of the HTTP API. */
public final class LinkDtos {
    private LinkDtos() { }

    public record CreateLinkRequest(
            @NotBlank @Size(max = 2048) String url,
            @Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$", message = "alias must be 3-32 characters of letters, digits, '-' or '_'")
            String alias) { }

    public record LinkResponse(String code, String shortUrl, String longUrl, Instant createdAt) { }

    public record ReferrerCount(String referrer, long clicks) { }

    public record StatsResponse(String code, long totalClicks, Map<String, Long> clicksPerDay,
                                List<ReferrerCount> topReferrers) { }
}
