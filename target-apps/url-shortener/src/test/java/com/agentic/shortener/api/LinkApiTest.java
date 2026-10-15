package com.agentic.shortener.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests of the HTTP API with the real Spring context and H2. Test URLs use public IP
 * literals (TEST-NET-3) so no DNS lookup is needed and the tests also run offline.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LinkApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private static String uniqueUrl() {
        return "http://203.0.113.10/" + UUID.randomUUID();
    }

    private MvcResult create(String url, String alias) throws Exception {
        String body = alias == null
                ? "{\"url\":\"" + url + "\"}"
                : "{\"url\":\"" + url + "\",\"alias\":\"" + alias + "\"}";
        return mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    @Test
    void createReturns201AndSevenCharacterCode() throws Exception {
        MvcResult r = create(uniqueUrl(), null);
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        JsonNode b = body(r);
        assertThat(b.get("code").asText()).hasSize(7);
        assertThat(b.get("shortUrl").asText()).endsWith("/" + b.get("code").asText());
    }

    @Test
    void creatingTheSameUrlTwiceReturnsTheSameLink() throws Exception {
        String url = uniqueUrl();
        String first = body(create(url, null)).get("code").asText();
        String second = body(create(url, null)).get("code").asText();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void customAliasIsUsedAndConflictsAreReported() throws Exception {
        String alias = "al" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult ok = create(uniqueUrl(), alias);
        assertThat(ok.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(ok).get("code").asText()).isEqualTo(alias);

        MvcResult conflict = create(uniqueUrl(), alias);
        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void reservedOrMalformedAliasIsRejected() throws Exception {
        assertThat(create(uniqueUrl(), "admin").getResponse().getStatus()).isEqualTo(400);
        assertThat(create(uniqueUrl(), "no spaces!").getResponse().getStatus()).isEqualTo(400);
        assertThat(create(uniqueUrl(), "ab").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void internalAndMalformedUrlsAreRejected() throws Exception {
        for (String url : new String[] {"http://localhost/x", "http://127.0.0.1/x", "http://10.0.0.5/x",
                "http://169.254.169.254/latest/meta-data/", "ftp://203.0.113.10/x", "not-a-url"}) {
            assertThat(create(url, null).getResponse().getStatus()).as(url).isEqualTo(400);
        }
        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void redirectAnswers302RecordsClicksAndShowsInStats() throws Exception {
        String url = uniqueUrl();
        String code = body(create(url, null)).get("code").asText();

        mvc.perform(get("/" + code).header("Referer", "https://news.example/story").header("User-Agent", "test-agent"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", url));
        mvc.perform(get("/" + code)).andExpect(status().isFound());

        MvcResult stats = mvc.perform(get("/api/v1/links/" + code + "/stats")).andExpect(status().isOk()).andReturn();
        JsonNode s = body(stats);
        assertThat(s.get("totalClicks").asLong()).isEqualTo(2);
        assertThat(s.get("clicksPerDay").size()).isEqualTo(1);
        assertThat(s.get("topReferrers").size()).isEqualTo(2);
    }

    @Test
    void detailsReturnTheOriginalUrl() throws Exception {
        String url = uniqueUrl();
        String code = body(create(url, null)).get("code").asText();
        MvcResult r = mvc.perform(get("/api/v1/links/" + code)).andExpect(status().isOk()).andReturn();
        assertThat(body(r).get("longUrl").asText()).isEqualTo(url);
    }

    @Test
    void unknownCodeReturns404ProblemDetails() throws Exception {
        mvc.perform(get("/zzzzzzz")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/links/zzzzzzz")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/links/zzzzzzz/stats")).andExpect(status().isNotFound());
    }

    @Test
    void deletedLinkStopsRedirecting() throws Exception {
        String code = body(create(uniqueUrl(), null)).get("code").asText();
        mvc.perform(get("/" + code)).andExpect(status().isFound());
        mvc.perform(delete("/api/v1/links/" + code)).andExpect(status().isNoContent());
        mvc.perform(get("/" + code)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/links/" + code)).andExpect(status().isNotFound());
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
