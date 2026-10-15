package com.agentic.orchestrator.agents;

/** What an agent reports back: a one-line summary and the rationale recorded in the decision lineage. */
public record AgentResult(String summary, String rationale) { }
