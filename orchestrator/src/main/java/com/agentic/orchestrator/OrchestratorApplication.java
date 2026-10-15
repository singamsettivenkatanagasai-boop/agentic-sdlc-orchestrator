package com.agentic.orchestrator;

import com.agentic.orchestrator.config.OrchestratorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Agentic SDLC orchestrator. Starts either as a REST service (default) or, with the
 * {@code scenario} profile, as a command-line runner that executes the demo scenarios and exits.
 */
@SpringBootApplication
@EnableConfigurationProperties(OrchestratorProperties.class)
public class OrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
