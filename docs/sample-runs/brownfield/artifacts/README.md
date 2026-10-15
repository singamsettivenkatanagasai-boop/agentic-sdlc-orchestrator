# URL Shortener

Add optional link expiry and per-IP rate limiting to the existing URL shortener without breaking its current API.

## API

| Method | Path | Handler |
| --- | --- | --- |
| POST | /api/v1/links | LinkController.create |
| GET | /api/v1/links/{code} | LinkController.get |
| DELETE | /api/v1/links/{code} | LinkController.delete |
| GET | /api/v1/links/{code}/stats | LinkController.stats |
| GET | /{code} | RedirectController.redirect |

The full contract is in `openapi.yaml`.

## Features

- Short links with optional custom alias; the same URL always maps to the same link
- HTTP 302 redirects with click tracking and statistics
- SSRF-safe URL validation
- RFC 7807 problem responses
- Optional link expiry (`expiresAt`); expired links answer 410 Gone
- Per-IP rate limiting; excess requests answer 429 with Retry-After

## Run and test

```
mvn spring-boot:run
mvn test
```

Classes: 21, controllers: [LinkController, RedirectController]

See `docs/design.md` for the design and `docs/security-review.md` for the security review.
