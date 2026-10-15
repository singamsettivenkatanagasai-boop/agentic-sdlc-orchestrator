package com.agentic.orchestrator.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Live client for the Anthropic Messages API (plain HTTP, no SDK dependency). */
public final class AnthropicLlmClient implements LlmClient {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final String endpoint;

    public AnthropicLlmClient(String apiKey, String model, int maxTokens, String endpoint) {
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.endpoint = endpoint;
    }

    @Override
    public boolean live() { return true; }

    @Override
    public String complete(String system, String user) throws IOException, InterruptedException {
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "system", system,
                "messages", List.of(Map.of("role", "user", "content", user)));
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofMinutes(5))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            String b = response.body();
            throw new IOException("LLM call failed: HTTP " + response.statusCode() + " "
                    + (b.length() > 500 ? b.substring(0, 500) : b));
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode block : JSON.readTree(response.body()).path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        return text.toString();
    }
}
