# Test plan

Derived from the requirement: Build a URL shortener service that creates short links, redirects visitors to the original URL and reports click statistics.

## Strategy

| Level | Scope | Tooling |
| --- | --- | --- |
| Unit | Code generator, URL validator, service rules | JUnit 5, AssertJ, Mockito |
| Integration | HTTP API end to end with the real Spring context and H2 | MockMvc |
| Failure and security | SSRF targets, alias conflicts, malformed input, unknown codes | JUnit 5 |
| Regression | Baseline for future changes | mvn test |

## Risk-based cases

- Create returns 201 and a 7-character code; creating the same URL again returns the same code
- Custom alias: accepted when free, 409 when taken by another URL, rejected when reserved or malformed
- Redirect returns 302 with Location and records exactly one click (referrer, user agent)
- Stats: total clicks, per-day buckets, top referrers
- Unknown or deleted code returns 404
- SSRF: localhost, 127.0.0.1, ::1, 10.x, 192.168.x, 169.254.169.254 and credential URLs are rejected

## Exit criteria

All tests green; no test depends on external network access.
