package com.agentic.orchestrator.llm;

import java.io.IOException;

/**
 * Thin abstraction over a language model. Two implementations exist: a live Anthropic client
 * and a replay client that reports {@code live() == false}, in which case agents fall back to
 * their deterministic implementations (the prototype always runs, with or without an API key).
 */
public interface LlmClient {
    /** True when calls reach a real model. */
    boolean live();

    String complete(String system, String user) throws IOException, InterruptedException;
}
