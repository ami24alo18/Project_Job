package com.amit.jobagent.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Job Application Agent API",
        version = "3.0",
        description = "Self-hosted candidate data plus authorized, deterministic job ingestion from manual, Lever, Greenhouse, and n8n email-alert sources. All examples are fictional."))
@SecurityScheme(name = "basicAuth", type = SecuritySchemeType.HTTP, scheme = "basic",
        description = "Temporary single-user Basic authentication configured with APP_SECURITY_USERNAME and APP_SECURITY_PASSWORD")
public class OpenApiConfiguration {
}
