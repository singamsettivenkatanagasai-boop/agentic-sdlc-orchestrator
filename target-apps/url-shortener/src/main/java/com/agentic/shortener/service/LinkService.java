package com.agentic.shortener.service;

import com.agentic.shortener.error.AliasConflictException;
import com.agentic.shortener.error.InvalidAliasException;
import com.agentic.shortener.error.LinkNotFoundException;
import com.agentic.shortener.model.Click;
import com.agentic.shortener.model.Link;
import com.agentic.shortener.repository.ClickRepository;
import com.agentic.shortener.repository.LinkRepository;
import com.agentic.shortener.validation.UrlValidator;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Business rules of the shortener: create, resolve, delete and statistics. */
@Service
public class LinkService {
    private static final Pattern ALIAS = Pattern.compile("^[A-Za-z0-9_-]{3,32}$");
    private static final Set<String> RESERVED = Set.of("api", "actuator", "error", "health", "admin", "h2-console");
    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final int TOP_REFERRERS = 5;

    /** Aggregated click statistics of one link. */
    public record Stats(String code, long totalClicks, Map<String, Long> clicksPerDay,
                        List<Map.Entry<String, Long>> topReferrers) { }

    private final LinkRepository links;
    private final ClickRepository clicks;
    private final UrlValidator validator;
    private final CodeGenerator codes;
    private final Clock clock;
    private final Cache<String, Link> cache;

    public LinkService(LinkRepository links, ClickRepository clicks, UrlValidator validator, CodeGenerator codes,
                       Clock clock,
                       @Value("${shortener.cache.ttl-seconds:60}") long cacheTtlSeconds,
                       @Value("${shortener.cache.max-size:10000}") long cacheMaxSize) {
        this.links = links;
        this.clicks = clicks;
        this.validator = validator;
        this.codes = codes;
        this.clock = clock;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(cacheTtlSeconds))
                .maximumSize(cacheMaxSize)
                .build();
    }

    /**
     * Creates a link. Idempotent: the same URL (without alias) returns the existing live link, and
     * the same alias with the same URL returns that link. A taken alias with a different URL is a conflict.
     */
    @Transactional
    public Link create(String rawUrl, String alias) {
        String url = validator.validate(rawUrl).toString();
        Instant now = clock.instant();

        if (alias != null && !alias.isBlank()) {
            validateAlias(alias);
            Optional<Link> existing = links.findByCode(alias);
            if (existing.isPresent()) {
                Link l = existing.get();
                if (!l.isDeleted() && l.getLongUrl().equals(url)) {
                    return l;
                }
                throw new AliasConflictException(alias);
            }
            return links.save(new Link(alias, url, now));
        }

        Optional<Link> same = links.findFirstByLongUrlAndDeletedFalse(url);
        if (same.isPresent()) {
            return same.get();
        }
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = codes.next();
            if (!links.existsByCode(code)) {
                return links.save(new Link(code, url, now));
            }
        }
        throw new IllegalStateException("could not allocate a unique short code");
    }

    /** Resolves a code for a redirect and records the click. */
    public URI resolve(String code, String referrer, String userAgent) {
        Link link = findLive(code);
        clicks.save(new Click(link.getId(), clock.instant(), referrer, userAgent));
        return URI.create(link.getLongUrl());
    }

    /** Link details (also used by the API). */
    public Link find(String code) {
        return findLive(code);
    }

    @Transactional
    public void delete(String code) {
        Link link = links.findByCode(code).filter(l -> !l.isDeleted()).orElseThrow(() -> new LinkNotFoundException(code));
        link.markDeleted();
        links.save(link);
        cache.invalidate(code);
    }

    @Transactional(readOnly = true)
    public Stats stats(String code) {
        Link link = findLive(code);
        List<Click> all = clicks.findByLinkId(link.getId());
        Map<String, Long> perDay = new TreeMap<>();
        Map<String, Long> byReferrer = new HashMap<>();
        for (Click c : all) {
            perDay.merge(c.getClickedAt().atZone(ZoneOffset.UTC).toLocalDate().toString(), 1L, Long::sum);
            String ref = c.getReferrer() == null || c.getReferrer().isBlank() ? "(direct)" : c.getReferrer();
            byReferrer.merge(ref, 1L, Long::sum);
        }
        List<Map.Entry<String, Long>> top = new ArrayList<>(byReferrer.entrySet());
        top.sort(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.<String, Long>comparingByKey()));
        return new Stats(code, all.size(), perDay, top.subList(0, Math.min(TOP_REFERRERS, top.size())));
    }

    private Link findLive(String code) {
        Link cached = cache.getIfPresent(code);
        if (cached != null) {
            return cached;
        }
        Link link = links.findByCode(code).filter(l -> !l.isDeleted()).orElseThrow(() -> new LinkNotFoundException(code));
        cache.put(code, link);
        return link;
    }

    private static void validateAlias(String alias) {
        if (!ALIAS.matcher(alias).matches()) {
            throw new InvalidAliasException("alias must be 3-32 characters of letters, digits, '-' or '_'");
        }
        if (RESERVED.contains(alias.toLowerCase(Locale.ROOT))) {
            throw new InvalidAliasException("alias '" + alias + "' is reserved");
        }
    }
}
