# Plan: Expire links automatically

**Meaning:** Links stop working after an optional expiry time, limiting how long a bad link stays live.

## Tasks

- Add an optional expiresAt to create and details
- Enforce expiry on redirect (HTTP 410)
- Add the database migration for expires_at

**Risk:** MEDIUM: schema change; clients must handle 410
