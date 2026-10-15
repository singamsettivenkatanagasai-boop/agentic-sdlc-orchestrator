package com.agentic.orchestrator.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Title and how-to text of the Swagger UI ({@code /swagger-ui.html}) and of {@code /v3/api-docs}. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI orchestratorApi() {
        return new OpenAPI().info(new Info()
                .title("Agentic SDLC Orchestrator API")
                .version("0.1.0")
                .description("""
                        Drive the agents from your browser.

                        1. **POST /runs**: pick an example (or write your own `requirement`) and press *Execute*. Copy the `runId`.
                        2. **GET /runs/{id}/pending**: shows what waits for a human. Empty means the agents are still working; try again.
                        3. **POST /runs/{id}/nodes/{nodeId}/approval**: approve or reject the waiting node (greenfield: design, release, signoff).
                        4. Repeat 2 and 3 until **GET /runs/{id}** shows `metrics.status` = `COMPLETED` (or `STOPPED`).
                        5. **GET /runs/{id}/report**: the full report.

                        Only one run can be active at a time.
                        """));
    }
}
