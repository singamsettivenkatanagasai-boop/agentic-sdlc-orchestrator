package com.agentic.shortener.validation;

import com.agentic.shortener.error.InvalidUrlException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The SSRF guard must reject everything that points into a private network. */
class UrlValidatorTest {

    /** Fake DNS: two named hosts, everything else is treated as an IP literal. */
    private final UrlValidator validator = new UrlValidator(host -> switch (host) {
        case "public.example" -> new InetAddress[] {InetAddress.getByAddress(host, new byte[] {93, (byte) 184, (byte) 216, 34})};
        case "internal.example.org" -> new InetAddress[] {InetAddress.getByAddress(host, new byte[] {10, 0, 0, 5})};
        case "missing.example" -> throw new UnknownHostException(host);
        default -> InetAddress.getAllByName(host);
    });

    @Test
    void acceptsPublicHttpAndHttpsUrls() {
        assertThat(validator.validate("http://public.example/a?b=c").toString()).isEqualTo("http://public.example/a?b=c");
        assertThat(validator.validate("  https://public.example/path  ").toString()).isEqualTo("https://public.example/path");
    }

    @Test
    void rejectsBlankMalformedAndNonHttpUrls() {
        assertRejected(null);
        assertRejected("   ");
        assertRejected("not a url");
        assertRejected("http://");
        assertRejected("ftp://public.example/file");
        assertRejected("javascript:alert(1)");
        assertRejected("/relative/path");
    }

    @Test
    void rejectsUrlsWithCredentials() {
        assertRejected("http://user:pass@public.example/");
    }

    @Test
    void rejectsLocalAndPrivateTargets() {
        assertRejected("http://localhost/admin");
        assertRejected("http://app.localhost/");
        assertRejected("http://127.0.0.1/");
        assertRejected("http://[::1]/");
        assertRejected("http://10.1.2.3/");
        assertRejected("http://172.16.0.9/");
        assertRejected("http://192.168.1.1/");
        assertRejected("http://169.254.169.254/latest/meta-data/");
        assertRejected("http://100.64.0.1/");
        assertRejected("http://0.0.0.0/");
        assertRejected("http://[fd00::1]/");
    }

    @Test
    void rejectsHostsThatResolveToPrivateAddresses() {
        assertRejected("http://internal.example.org/");
    }

    @Test
    void rejectsHostsThatCannotBeResolved() {
        assertRejected("http://missing.example/");
    }

    @Test
    void rejectsOverlongUrls() {
        assertRejected("http://public.example/" + "a".repeat(UrlValidator.MAX_LENGTH));
    }

    private void assertRejected(String url) {
        assertThatThrownBy(() -> validator.validate(url))
                .as("url %s", url)
                .isInstanceOf(InvalidUrlException.class);
    }
}
