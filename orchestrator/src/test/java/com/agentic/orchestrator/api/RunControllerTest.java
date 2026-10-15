package com.agentic.orchestrator.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The REST control plane with the real Spring context. Uses the ambiguous scenario because it
 * needs no Maven build, so the test is fast and offline. Working folders are temporary.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RunControllerTest {

    @DynamicPropertySource
    static void folders(DynamicPropertyRegistry registry) throws IOException {
        Path base = Files.createTempDirectory("orchestrator-rest-test");
        registry.add("agentic.workspace-dir", () -> base.resolve("ws").toString());
        registry.add("agentic.export-dir", () -> base.resolve("runs").toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private JsonNode getJson(String url) throws Exception {
        return json.readTree(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String start(String body) throws Exception {
        String reply = mvc.perform(post("/runs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return json.readTree(reply).get("runId").asText();
    }

    private String waitForFinish(String runId) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            String s = getJson("/runs/" + runId).get("metrics").get("status").asText();
            if (s.equals("COMPLETED") || s.equals("STOPPED")) {
                return s;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("run did not finish in time");
    }

    @Test
    void autoApprovedRunCompletesAndExposesGraphEventsReportAndLineage() throws Exception {
        String id = start("{\"scenario\":\"AMBIGUOUS\",\"autoApprove\":true,"
                + "\"clarificationAnswers\":{\"link-expiry\":true,\"abuse-rate-limiting\":true}}");
        assertEquals("COMPLETED", waitForFinish(id));

        JsonNode graph = getJson("/runs/" + id + "/graph");
        assertEquals(5, graph.size(), "interpret, two plans, scoped-plan, signoff");

        JsonNode events = getJson("/runs/" + id + "/events");
        assertTrue(events.get("chainVerified").asBoolean());
        assertTrue(events.get("events").size() > 10);

        String report = mvc.perform(get("/runs/" + id + "/report")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(report.contains("Run report"));

        JsonNode lineage = getJson("/runs/" + id + "/lineage?path=docs/scoped-plan.md");
        assertEquals("scoped-plan", lineage.get("producerNode").asText());
    }

    @Test
    void aHumanCanAnswerClarificationAndApprovalOverRest() throws Exception {
        String id = start("{\"scenario\":\"AMBIGUOUS\",\"autoApprove\":false}");

        waitForPending(id, "interpret");
        mvc.perform(post("/runs/" + id + "/nodes/interpret/approval").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"approver\":\"alice\",\"comment\":\"expiry and rate limit only\","
                                + "\"answers\":{\"link-expiry\":true,\"abuse-rate-limiting\":true}}"))
                .andExpect(status().isNoContent());

        waitForPending(id, "signoff");
        mvc.perform(post("/runs/" + id + "/nodes/signoff/approval").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"approver\":\"alice\",\"comment\":\"ship the plan\"}"))
                .andExpect(status().isNoContent());

        assertEquals("COMPLETED", waitForFinish(id));
        JsonNode events = getJson("/runs/" + id + "/events").get("events");
        boolean aliceDecided = false;
        for (JsonNode e : events) {
            aliceDecided |= e.get("type").asText().equals("APPROVAL_DECIDED") && e.get("actor").asText().equals("human:alice");
        }
        assertTrue(aliceDecided, "the audit trail must name the human who decided");
    }

    @Test
    void unknownRunsAndMissingApproverAreRejected() throws Exception {
        mvc.perform(get("/runs/does-not-exist")).andExpect(status().isNotFound());
        mvc.perform(post("/runs/does-not-exist/stop")).andExpect(status().isNotFound());
    }

    @Test
    void theApiIsDescribedForSwagger() throws Exception {
        String docs = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(docs.contains("Agentic SDLC Orchestrator API"));
        assertTrue(docs.contains("/runs/{id}/nodes/{nodeId}/approval"));
        assertTrue(docs.contains("Greenfield with your own requirement"));
    }

    @Test
    void aRunWithoutScenarioIsABadRequest() throws Exception {
        mvc.perform(post("/runs").contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"no-scenario\"}"))
                .andExpect(status().isBadRequest());
    }

    private void waitForPending(String runId, String nodeId) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            for (JsonNode p : getJson("/runs/" + runId + "/pending")) {
                if (p.get("nodeId").asText().equals(nodeId)) {
                    return;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("no pending request for " + nodeId);
    }
}
