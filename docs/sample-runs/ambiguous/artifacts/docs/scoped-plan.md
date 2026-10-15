# Scoped plan

## In scope

- Expire links automatically (MEDIUM: schema change; clients must handle 410)
- Limit abuse per client (LOW: in-memory limiter is per instance; needs a shared store when scaled out)

## Out of scope

- Block malicious destinations - not confirmed by a human
- Authenticated link management - not confirmed by a human

## Suggested order

Smallest-risk items first: rate limiting, then expiry (schema change), then anything that needs a new external dependency.
