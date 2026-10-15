# Design

## Goal

Build a URL shortener service that creates short links, redirects visitors to the original URL and reports click statistics.

## Components

| Component | Responsibility |
| --- | --- |
| LinkController | REST API for creating, reading, deleting links and stats |
| RedirectController | GET /{code}: resolves a code and answers 302 |
| LinkService | Business rules: idempotent create, alias rules, click recording, stats |
| UrlValidator | SSRF guard: scheme, length, credentials, internal addresses |
| CodeGenerator | Random base62 short codes |
| LinkRepository / ClickRepository | Spring Data JPA persistence |
| GlobalExceptionHandler | RFC 7807 problem responses |

## Data model

- links(id, code UNIQUE (generated or custom alias), long_url, created_at, deleted)
- clicks(id, link_id, ts, referrer, user_agent)

## Key decisions and trade-offs

| Decision | Alternative | Why |
| --- | --- | --- |
| HTTP 302 redirect | 301 (cacheable) | 301 would let browsers skip the service and hide clicks from analytics |
| Random 7-char base62 code | Sequential id encoded in base62 | Unguessable codes; collisions are cheap to retry. A DB sequence would remove retries at scale |
| In-process Caffeine cache for redirects | Redis | No extra infrastructure for the prototype; stale entries are bounded by a 60 s TTL |
| H2 in-memory | PostgreSQL | Zero setup for reviewers; schema is plain JPA so the swap is configuration |

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| SSRF through shortened URLs | UrlValidator blocks loopback, private, link-local and credential URLs |
| Alias squatting / collisions | Alias pattern and reserved words; conflict returns 409 |
| Unbounded click table growth | Retention policy needed in production (documented limitation) |
