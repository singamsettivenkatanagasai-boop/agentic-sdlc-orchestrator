package com.agentic.shortener.validation;

import com.agentic.shortener.error.InvalidUrlException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Validates URLs before they are shortened. Besides the obvious checks (scheme, length) it blocks
 * targets that point into private networks - loopback, site-local, link-local (cloud metadata at
 * 169.254.169.254), carrier-grade NAT and IPv6 unique-local ranges - so a short link cannot be
 * used to reach internal resources (SSRF protection).
 *
 * <p>Known limitation: the host is resolved once, at creation time. A hostile DNS server could
 * answer differently later (DNS rebinding); a production system would also re-check on redirect.
 */
@Component
public class UrlValidator {
    public static final int MAX_LENGTH = 2048;

    /** Hostname lookup, injectable so tests do not depend on real DNS. */
    @FunctionalInterface
    public interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final HostResolver resolver;

    @Autowired
    public UrlValidator() {
        this(InetAddress::getAllByName);
    }

    public UrlValidator(HostResolver resolver) {
        this.resolver = resolver;
    }

    /** Returns the normalised URI or throws {@link InvalidUrlException}. */
    public URI validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidUrlException("url must not be blank");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new InvalidUrlException("url is longer than " + MAX_LENGTH + " characters");
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("url is not well-formed");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new InvalidUrlException("only http and https urls are allowed");
        }
        if (uri.getUserInfo() != null) {
            throw new InvalidUrlException("urls with embedded credentials are not allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("url has no host");
        }
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.equals("localhost") || lower.endsWith(".localhost") || lower.endsWith(".local")
                || lower.endsWith(".internal")) {
            throw new InvalidUrlException("urls that point to local or internal hosts are not allowed");
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(stripBrackets(host));
        } catch (UnknownHostException e) {
            throw new InvalidUrlException("host cannot be resolved");
        }
        for (InetAddress a : addresses) {
            if (isInternal(a)) {
                throw new InvalidUrlException("urls that point to private or internal addresses are not allowed");
            }
        }
        return uri;
    }

    private static String stripBrackets(String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    static boolean isInternal(InetAddress a) {
        if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isSiteLocalAddress()
                || a.isLinkLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            // 0.0.0.0/8 and carrier-grade NAT 100.64.0.0/10
            return first == 0 || (first == 100 && second >= 64 && second <= 127);
        }
        if (a instanceof Inet6Address) {
            // unique local addresses fc00::/7
            return (b[0] & 0xfe) == 0xfc;
        }
        return false;
    }
}
