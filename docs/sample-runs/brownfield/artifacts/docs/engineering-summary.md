# Engineering summary

Scenario: **BROWNFIELD**

## Requirement

> Add optional link expiry and per-IP rate limiting to the existing URL shortener without breaking its current API.

Amendments:

- Clients must know when to retry: document the Retry-After header on 429 responses in the API contract.

## Work breakdown

| Node | Agent | Risk | State | Attempts |
| --- | --- | --- | --- | --- |
| requirements | requirements | LOW | DONE | 1 |
| impact | architect | LOW | DONE | 1 |
| design | architect | HIGH | DONE | 2 |
| test-plan | test | LOW | DONE | 1 |
| implement | developer | MEDIUM | DONE | 2 |
| tests | test | MEDIUM | DONE | 1 |
| security | security | MEDIUM | DONE | 1 |
| docs | docs | LOW | DONE | 1 |
| release | release | HIGH | DONE | 1 |
| signoff | reviewer | HIGH | AWAITING SIGN-OFF | 1 |
| migration | golden-developer | HIGH | DONE | 2 |

## Decisions and approvals

| Id | Node | Actor | Decision | Rationale |
| --- | --- | --- | --- | --- |
| D-1 | requirements | requirements | completed | kept scope to what the requirement states; recorded 6 assumptions explicitly instead of guessing silently |
| D-2 | test-plan | test | completed | tests are planned from the requirement in parallel with the design, so they are not biased by the implementation |
| D-3 | impact | architect | completed | impact derived from the parsed code structure and the concerns found in the requirement |
| D-4 | design | architect | completed | contract-first: the OpenAPI file is the reviewed artefact that the implementation must follow |
| D-5 | design | human:simulated-reviewer | approved | reviewed and approved (simulated) |
| D-6 | migration | golden-developer | completed | deterministic change set; the need for it was established by the impact analysis |
| D-7 | migration | human:simulated-reviewer | approved | reviewed and approved (simulated) |
| D-8 | implement | developer | completed | implemented from the approved design and contract |
| D-9 | design | requester | requirement-changed | Clients must know when to retry: document the Retry-After header on 429 responses in the API contract. |
| D-10 | design | architect | completed | contract-first: the OpenAPI file is the reviewed artefact that the implementation must follow |
| D-11 | design | human:simulated-reviewer | approved | reviewed and approved (simulated) |
| D-12 | migration | golden-developer | completed | deterministic change set; the need for it was established by the impact analysis |
| D-13 | migration | human:simulated-reviewer | approved | reviewed and approved (simulated) |
| D-14 | implement | developer | completed | retry with feedback from the previous failure |
| D-15 | tests | test | completed | the result is recorded in the run context; the exit gate, not the agent, decides acceptance |
| D-16 | security | security | completed | blocking findings fail the node; the agent cannot waive them |
| D-17 | docs | docs | completed | documentation is derived from the code so it cannot drift from the implementation |
| D-18 | release | release | completed | release is blocked unless every check passes; the human approves afterwards |
| D-19 | release | human:simulated-reviewer | approved | reviewed and approved (simulated) |

## Assumptions

- Redirects use HTTP 302 (not 301) so every click reaches the service and can be counted.
- Short codes are 7 random base62 characters; collisions are retried up to 5 times.
- Storage is in-memory H2 for the prototype; production would use PostgreSQL.
- No authentication is in scope: any caller may create and delete links.
- Expiry is evaluated at request time in UTC; a missing expiresAt means the link never expires.
- The rate limit is keyed by remote IP with an in-memory token bucket; behind a proxy, X-Forwarded-For handling is a follow-up.

## Artefacts

| Path | Version | Producer | SHA-256 |
| --- | --- | --- | --- |
| README.md | 1 | docs | d63961feabac |
| docs/CHANGELOG.md | 1 | docs | a353d522d35a |
| docs/design.md | 2 | design | 313b95483ada |
| docs/impact-analysis.md | 1 | impact | 368b765b61df |
| docs/release-notes.md | 1 | release | 177bb448d891 |
| docs/requirements.md | 1 | requirements | 9bfee3ea06bb |
| docs/security-review.md | 1 | security | 45f5e6a5491d |
| docs/test-plan.md | 1 | test-plan | 594d95369843 |
| docs/test-report.md | 1 | tests | 99faa9f6e15a |
| openapi.yaml | 1 | design | f99a652d3916 |
| src/main/java/com/agentic/shortener/api/LinkController.java | 1 | implement | b8ce33f3af33 |
| src/main/java/com/agentic/shortener/dto/LinkDtos.java | 1 | implement | 2b85faa9407b |
| src/main/java/com/agentic/shortener/error/GlobalExceptionHandler.java | 1 | implement | ddc955c89ebe |
| src/main/java/com/agentic/shortener/error/InvalidExpiryException.java | 1 | implement | fffbb7e3ee9d |
| src/main/java/com/agentic/shortener/error/LinkExpiredException.java | 1 | implement | 9774aa69311a |
| src/main/java/com/agentic/shortener/model/Link.java | 1 | implement | de6d06fad7ff |
| src/main/java/com/agentic/shortener/ratelimit/RateLimitFilter.java | 1 | implement | 37113342d73c |
| src/main/java/com/agentic/shortener/ratelimit/TokenBucket.java | 1 | implement | 980ab19916c7 |
| src/main/java/com/agentic/shortener/repository/LinkRepository.java | 1 | implement | 265a3d128fc1 |
| src/main/java/com/agentic/shortener/service/LinkService.java | 1 | implement | 3e2d92191068 |
| src/main/resources/application.properties | 1 | implement | 9836f7ffc3c1 |
| src/main/resources/db/migration/V2__add_link_expiry.sql | 1 | migration | dfe2cc50403f |
| src/test/java/com/agentic/shortener/api/LinkApiTest.java | 1 | tests | 1c6889128a73 |
| src/test/java/com/agentic/shortener/api/LinkExpiryApiTest.java | 1 | tests | 1c389f70eff8 |
| src/test/java/com/agentic/shortener/api/RateLimitApiTest.java | 1 | tests | c2a56344dc98 |
| src/test/java/com/agentic/shortener/ratelimit/TokenBucketTest.java | 1 | tests | 605ba14e38af |
| src/test/java/com/agentic/shortener/service/CodeGeneratorTest.java | 1 | tests | e73a4bd617be |
| src/test/java/com/agentic/shortener/validation/UrlValidatorTest.java | 1 | tests | 283c1580a903 |

## Remaining risks

- No authentication; per-instance rate limiting; in-memory database
- Generated code is reviewed by gates and tests, not by formal verification
