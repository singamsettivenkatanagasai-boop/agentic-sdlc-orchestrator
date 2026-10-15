# Interpretations of the requirement

> Make links safer.

## Why this is ambiguous

"Safer" can mean protection from malicious destinations, limited link lifetime, protection from abuse, or restricted access. Each reading has a different cost, risk and API impact, so the scope must be chosen by a human before any design or code is produced.

## Candidate interpretations

| Id | Interpretation | Meaning | Question for the human | Recommended | Risk |
| --- | --- | --- | --- | --- | --- |
| malware-blocking | Block malicious destinations | Reject or flag URLs that appear on a threat blocklist, at creation and at redirect time. | Should links to known malicious sites be blocked, and who maintains the blocklist? | yes | HIGH: false positives block legitimate links; adds an external dependency |
| link-expiry | Expire links automatically | Links stop working after an optional expiry time, limiting how long a bad link stays live. | Should links be able to expire, and should expired links answer 410 Gone? | yes | MEDIUM: schema change; clients must handle 410 |
| abuse-rate-limiting | Limit abuse per client | Throttle link creation and redirects per client IP to stop automated abuse. | Should requests be rate limited per IP, and what limit is acceptable? | yes | LOW: in-memory limiter is per instance; needs a shared store when scaled out |
| access-control | Authenticated link management | Only authenticated owners may delete links or read their statistics. | Is authentication and per-owner authorisation in scope? | no | HIGH: large scope, changes the whole API surface |

## Decision needed

Confirm which interpretations are in scope. Only confirmed ones are planned.
