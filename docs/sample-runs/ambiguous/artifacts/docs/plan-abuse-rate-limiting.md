# Plan: Limit abuse per client

**Meaning:** Throttle link creation and redirects per client IP to stop automated abuse.

## Tasks

- Add a per-IP token bucket filter
- Answer HTTP 429 with Retry-After
- Make the limit configurable

**Risk:** LOW: in-memory limiter is per instance; needs a shared store when scaled out
