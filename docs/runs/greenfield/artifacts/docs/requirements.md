# Requirements

## Original requirement

> Build a URL shortener service that creates short links, redirects visitors to the original URL and reports click statistics.

## Normalised problem statement

Deliver a stateless HTTP service that shortens URLs, redirects visitors and reports click analytics, protected against SSRF and malformed input.

## Functional requirements

- FR-1 Create a short link from a long http(s) URL, with an optional custom alias; creating the same URL again returns the same link
- FR-2 Redirect a short code to its target with HTTP 302 and record a click (timestamp, referrer, user agent)
- FR-3 Return link details and soft-delete a link
- FR-4 Report click statistics: total clicks, clicks per day, top referrers

## Non-functional requirements

- NFR-1 Errors use RFC 7807 problem details
- NFR-2 User-supplied URLs are validated (scheme, length, internal addresses)
- NFR-3 Health endpoint exposed for monitoring
- NFR-4 Behaviour covered by automated unit and integration tests

## Acceptance criteria

- AC-1 Given a valid URL, when POST /api/v1/links is called, then 201 and a 7-character code are returned
- AC-2 Given an existing code, when GET /{code} is called, then 302 with the original URL in Location and one click is recorded
- AC-3 Given an alias already used for another URL, when creating a link, then 409 is returned
- AC-4 Given a URL that points to localhost or a private address, when creating a link, then 400 is returned (SSRF protection)

## Assumptions and resolved ambiguities

- Redirects use HTTP 302 (not 301) so every click reaches the service and can be counted.
- Short codes are 7 random base62 characters; collisions are retried up to 5 times.
- Storage is in-memory H2 for the prototype; production would use PostgreSQL.
- No authentication is in scope: any caller may create and delete links.

## Out of scope

- Authentication and authorisation
- Custom domains
- Distributed rate limiting and caching
