# Engineering summary

Scenario: **GREENFIELD**

## Requirement

> Build a URL shortener service that creates short links, redirects visitors to the original URL and reports click statistics.

## Work breakdown

| Node | Agent | Risk | State | Attempts |
| --- | --- | --- | --- | --- |
| requirements | requirements | LOW | DONE | 1 |
| design | architect | HIGH | DONE | 1 |
| test-plan | test | LOW | DONE | 1 |
| implement | developer | MEDIUM | DONE | 1 |
| tests | test | MEDIUM | DONE | 1 |
| security | security | MEDIUM | DONE | 1 |
| docs | docs | LOW | DONE | 1 |
| release | release | HIGH | DONE | 1 |
| signoff | reviewer | HIGH | AWAITING SIGN-OFF | 1 |

## Decisions and approvals

| Id | Node | Actor | Decision | Rationale |
| --- | --- | --- | --- | --- |
| D-1 | requirements | requirements | completed | kept scope to what the requirement states; recorded 4 assumptions explicitly instead of guessing silently |
| D-2 | test-plan | test | completed | tests are planned from the requirement in parallel with the design, so they are not biased by the implementation |
| D-3 | design | architect | completed | contract-first: the OpenAPI file is the reviewed artefact that the implementation must follow |
| D-4 | design | human:simulated-reviewer | approved | reviewed and approved (simulated) |
| D-5 | implement | developer | completed | implemented from the approved design and contract |
| D-6 | tests | test | completed | the result is recorded in the run context; the exit gate, not the agent, decides acceptance |
| D-7 | security | security | completed | blocking findings fail the node; the agent cannot waive them |
| D-8 | docs | docs | completed | documentation is derived from the code so it cannot drift from the implementation |
| D-9 | release | release | completed | release is blocked unless every check passes; the human approves afterwards |
| D-10 | release | human:simulated-reviewer | approved | reviewed and approved (simulated) |

## Assumptions

- Redirects use HTTP 302 (not 301) so every click reaches the service and can be counted.
- Short codes are 7 random base62 characters; collisions are retried up to 5 times.
- Storage is in-memory H2 for the prototype; production would use PostgreSQL.
- No authentication is in scope: any caller may create and delete links.

## Artefacts

| Path | Version | Producer | SHA-256 |
| --- | --- | --- | --- |
| README.md | 1 | docs | 0c6dd62c1f4c |
| docs/CHANGELOG.md | 1 | docs | 9a39a59ef8d9 |
| docs/design.md | 1 | design | 6c089dafa586 |
| docs/release-notes.md | 1 | release | 5c36e5f3567f |
| docs/requirements.md | 1 | requirements | 07ddd24182b7 |
| docs/security-review.md | 1 | security | 2e466ae47616 |
| docs/test-plan.md | 1 | test-plan | bf12b68911d9 |
| docs/test-report.md | 1 | tests | 68a515c9979f |
| openapi.yaml | 1 | design | 488e995a81b4 |
| pom.xml | 1 | implement | e2d162df4424 |
| src/main/java/com/agentic/shortener/UrlShortenerApplication.java | 1 | implement | 97f8f9e84477 |
| src/main/java/com/agentic/shortener/api/LinkController.java | 1 | implement | 05871d762310 |
| src/main/java/com/agentic/shortener/api/RedirectController.java | 1 | implement | 82931653c712 |
| src/main/java/com/agentic/shortener/dto/LinkDtos.java | 1 | implement | 0b81a5d06620 |
| src/main/java/com/agentic/shortener/error/AliasConflictException.java | 1 | implement | 904c87287cc1 |
| src/main/java/com/agentic/shortener/error/GlobalExceptionHandler.java | 1 | implement | e2d26f21678d |
| src/main/java/com/agentic/shortener/error/InvalidAliasException.java | 1 | implement | a99eb3e08c6a |
| src/main/java/com/agentic/shortener/error/InvalidUrlException.java | 1 | implement | a4c5e8f00e23 |
| src/main/java/com/agentic/shortener/error/LinkNotFoundException.java | 1 | implement | 857cbb3e4e2d |
| src/main/java/com/agentic/shortener/model/Click.java | 1 | implement | a107581d7d82 |
| src/main/java/com/agentic/shortener/model/Link.java | 1 | implement | aab58ad58ceb |
| src/main/java/com/agentic/shortener/repository/ClickRepository.java | 1 | implement | 457ff4fb9254 |
| src/main/java/com/agentic/shortener/repository/LinkRepository.java | 1 | implement | 556df6d72bff |
| src/main/java/com/agentic/shortener/service/CodeGenerator.java | 1 | implement | 5094eab074d9 |
| src/main/java/com/agentic/shortener/service/LinkService.java | 1 | implement | b8859e342964 |
| src/main/java/com/agentic/shortener/validation/UrlValidator.java | 1 | implement | 3c14323b486e |
| src/main/resources/application.properties | 1 | implement | 112e1a466f05 |
| src/test/java/com/agentic/shortener/api/LinkApiTest.java | 1 | tests | 1c6889128a73 |
| src/test/java/com/agentic/shortener/service/CodeGeneratorTest.java | 1 | tests | e73a4bd617be |
| src/test/java/com/agentic/shortener/validation/UrlValidatorTest.java | 1 | tests | 283c1580a903 |

## Remaining risks

- No authentication; per-instance rate limiting; in-memory database
- Generated code is reviewed by gates and tests, not by formal verification
