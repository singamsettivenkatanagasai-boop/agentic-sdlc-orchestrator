# Requirements

## Original requirement

> Add optional link expiry and per-IP rate limiting to the existing URL shortener without breaking its current API.

## Normalised problem statement

Extend the existing URL shortener without breaking its API: Add optional link expiry and per-IP rate limiting to the existing URL shortener without breaking its current API.

## Functional requirements

- FR-0 All existing endpoints keep their current contract (no regression)
- FR-5 A link may carry an optional expiry time; once expired it answers HTTP 410 Gone
- FR-6 Requests are rate limited per client IP; excess requests get HTTP 429 with a Retry-After header

## Non-functional requirements

- NFR-1 Errors use RFC 7807 problem details
- NFR-2 User-supplied URLs are validated (scheme, length, internal addresses)
- NFR-3 Health endpoint exposed for monitoring
- NFR-4 Behaviour covered by automated unit and integration tests

## Acceptance criteria

- AC-0 Given the existing test suite, when the change is applied, then every existing test still passes
- AC-5 Given a link whose expiry has passed, when GET /{code} is called, then 410 is returned and no click is recorded
- AC-6 Given a client that exceeded its limit, when it calls the API again, then 429 and Retry-After are returned

## Assumptions and resolved ambiguities

- Redirects use HTTP 302 (not 301) so every click reaches the service and can be counted.
- Short codes are 7 random base62 characters; collisions are retried up to 5 times.
- Storage is in-memory H2 for the prototype; production would use PostgreSQL.
- No authentication is in scope: any caller may create and delete links.
- Expiry is evaluated at request time in UTC; a missing expiresAt means the link never expires.
- The rate limit is keyed by remote IP with an in-memory token bucket; behind a proxy, X-Forwarded-For handling is a follow-up.

## Out of scope

- Authentication and authorisation
- Custom domains
- Distributed rate limiting and caching
