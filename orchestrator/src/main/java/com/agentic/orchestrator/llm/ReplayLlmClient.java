package com.agentic.orchestrator.llm;

/** Used when no API key is configured: agents run in deterministic replay mode. */
public final class ReplayLlmClient implements LlmClient {
    @Override
    public boolean live() { return false; }

    @Override
    public String complete(String system, String user) {
        throw new UnsupportedOperationException("no LLM configured (set ANTHROPIC_API_KEY for live mode)");
    }
}
