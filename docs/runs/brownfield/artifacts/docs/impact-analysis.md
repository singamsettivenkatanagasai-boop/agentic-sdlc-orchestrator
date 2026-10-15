# Impact analysis

Concerns detected in the requirement: expiry, rate-limiting

## Code structure (parsed with JavaParser)

| Kind | Class | File | Details |
| --- | --- | --- | --- |
| class | UrlShortenerApplication | src/main/java/com/agentic/shortener/UrlShortenerApplication.java |  |
| controller | LinkController | src/main/java/com/agentic/shortener/api/LinkController.java | POST /api/v1/links, GET /api/v1/links/{code}, DELETE /api/v1/links/{code}, GET /api/v1/links/{code}/stats |
| controller | RedirectController | src/main/java/com/agentic/shortener/api/RedirectController.java | GET /{code:[A-Za-z0-9_-]+} |
| class | LinkDtos | src/main/java/com/agentic/shortener/dto/LinkDtos.java |  |
| class | AliasConflictException | src/main/java/com/agentic/shortener/error/AliasConflictException.java |  |
| error-handler | GlobalExceptionHandler | src/main/java/com/agentic/shortener/error/GlobalExceptionHandler.java |  |
| class | InvalidAliasException | src/main/java/com/agentic/shortener/error/InvalidAliasException.java |  |
| class | InvalidUrlException | src/main/java/com/agentic/shortener/error/InvalidUrlException.java |  |
| class | LinkNotFoundException | src/main/java/com/agentic/shortener/error/LinkNotFoundException.java |  |
| entity | Click | src/main/java/com/agentic/shortener/model/Click.java | id, linkId, clickedAt, referrer, userAgent |
| entity | Link | src/main/java/com/agentic/shortener/model/Link.java | id, code, longUrl, createdAt, deleted |
| repository | ClickRepository | src/main/java/com/agentic/shortener/repository/ClickRepository.java |  |
| repository | LinkRepository | src/main/java/com/agentic/shortener/repository/LinkRepository.java |  |
| component | CodeGenerator | src/main/java/com/agentic/shortener/service/CodeGenerator.java |  |
| service | LinkService | src/main/java/com/agentic/shortener/service/LinkService.java | create(), resolve(), find(), delete(), stats() |
| component | UrlValidator | src/main/java/com/agentic/shortener/validation/UrlValidator.java |  |
| class | HostResolver | src/main/java/com/agentic/shortener/validation/UrlValidator.java |  |

## Endpoints

| Method | Path | Handler |
| --- | --- | --- |
| POST | /api/v1/links | LinkController.create |
| GET | /api/v1/links/{code} | LinkController.get |
| DELETE | /api/v1/links/{code} | LinkController.delete |
| GET | /api/v1/links/{code}/stats | LinkController.stats |
| GET | /{code:[A-Za-z0-9_-]+} | RedirectController.redirect |

## Impacted components

| Component | Why it is impacted |
| --- | --- |
| Link | gains an expires_at attribute and an isExpired check |
| LinkService | create must accept an expiry; resolve and get must enforce it |
| LinkController | request and response contract gains expiresAt; 410 Gone for expired links; all endpoints are protected by a new servlet filter (cross-cutting, no code change inside) |
| RedirectController | request and response contract gains expiresAt; 410 Gone for expired links; all endpoints are protected by a new servlet filter (cross-cutting, no code change inside) |
| GlobalExceptionHandler | must map the new LinkExpired condition to HTTP 410; problem-details format is reused for HTTP 429 responses |

## Change classification

- Database schema change: **yes**
- Public API contract change: **yes**
