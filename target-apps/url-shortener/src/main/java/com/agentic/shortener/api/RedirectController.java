package com.agentic.shortener.api;

import com.agentic.shortener.service.LinkService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Public redirect endpoint. Answers 302 (not 301) so browsers do not cache the redirect and
 * every visit reaches the service and is counted.
 */
@RestController
public class RedirectController {
    private final LinkService service;

    public RedirectController(LinkService service) {
        this.service = service;
    }

    @GetMapping("/{code:[A-Za-z0-9_-]+}")
    public ResponseEntity<Void> redirect(@PathVariable String code,
                                         @RequestHeader(value = HttpHeaders.REFERER, required = false) String referrer,
                                         @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        URI target = service.resolve(code, referrer, userAgent);
        return ResponseEntity.status(HttpStatus.FOUND).location(target).build();
    }
}
