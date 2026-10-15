package com.agentic.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under the {@code agentic.*} prefix (see application.properties). */
@ConfigurationProperties(prefix = "agentic")
public class OrchestratorProperties {
    /** The single working folder agents build in (relative paths are resolved against the repository root). */
    private String workspaceDir = "workspaces/demo";
    /** Where run evidence (events, report, documents) is written. */
    private String exportDir = "data/runs";
    /** Scenario to execute when the {@code scenario} profile is active. */
    private String scenario = "greenfield";
    /** Build the generated project with {@code mvn -o} (offline) once dependencies are cached. */
    private boolean mavenOffline = false;
    private int mavenTimeoutMinutes = 10;
    /** Maven executable for the build/test gates; empty means {@code mvn} ({@code mvn.cmd} on Windows) from the PATH. */
    private String mavenCommand = "";
    private final Llm llm = new Llm();

    /** Language model settings. Without an API key the agents replay the verified implementation. */
    public static class Llm {
        private String apiKey = "";
        private String model = "claude-sonnet-5-5";
        private int maxTokens = 16000;
        private String endpoint = "https://api.anthropic.com/v1/messages";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    }

    public String getWorkspaceDir() { return workspaceDir; }
    public void setWorkspaceDir(String workspaceDir) { this.workspaceDir = workspaceDir; }
    public String getExportDir() { return exportDir; }
    public void setExportDir(String exportDir) { this.exportDir = exportDir; }
    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public boolean isMavenOffline() { return mavenOffline; }
    public void setMavenOffline(boolean mavenOffline) { this.mavenOffline = mavenOffline; }
    public int getMavenTimeoutMinutes() { return mavenTimeoutMinutes; }
    public void setMavenTimeoutMinutes(int mavenTimeoutMinutes) { this.mavenTimeoutMinutes = mavenTimeoutMinutes; }
    public String getMavenCommand() { return mavenCommand; }
    public void setMavenCommand(String mavenCommand) { this.mavenCommand = mavenCommand; }
    public Llm getLlm() { return llm; }
}
