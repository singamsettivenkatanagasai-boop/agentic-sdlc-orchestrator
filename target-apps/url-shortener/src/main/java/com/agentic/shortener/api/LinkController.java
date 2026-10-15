package com.agentic.shortener.api;

import com.agentic.shortener.dto.LinkDtos.CreateLinkRequest;
import com.agentic.shortener.dto.LinkDtos.LinkResponse;
import com.agentic.shortener.dto.LinkDtos.ReferrerCount;
import com.agentic.shortener.dto.LinkDtos.StatsResponse;
import com.agentic.shortener.model.Link;
import com.agentic.shortener.service.LinkService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/** Management API: create, read, delete and statistics of short links. */
@RestController
@RequestMapping("/api/v1/links")
public class LinkController {
    private final LinkService service;

    public LinkController(LinkService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        Link link = service.create(request.url(), request.alias());
        LinkResponse body = toResponse(link);
        return ResponseEntity.created(URI.create("/api/v1/links/" + link.getCode())).body(body);
    }

    @GetMapping("/{code}")
    public LinkResponse get(@PathVariable String code) {
        return toResponse(service.find(code));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        service.delete(code);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{code}/stats")
    public StatsResponse stats(@PathVariable String code) {
        LinkService.Stats s = service.stats(code);
        return new StatsResponse(s.code(), s.totalClicks(), s.clicksPerDay(),
                s.topReferrers().stream().map(e -> new ReferrerCount(e.getKey(), e.getValue())).toList());
    }

    private static LinkResponse toResponse(Link link) {
        String shortUrl = ServletUriComponentsBuilder.fromCurrentContextPath().path("/{code}")
                .buildAndExpand(link.getCode()).toUriString();
        return new LinkResponse(link.getCode(), shortUrl, link.getLongUrl(), link.getCreatedAt());
    }
}
