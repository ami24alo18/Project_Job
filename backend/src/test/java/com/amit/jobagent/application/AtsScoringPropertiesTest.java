package com.amit.jobagent.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AtsScoringPropertiesTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(ApplicationGenerationConfiguration.class)
            .withPropertyValues(
                    "job-agent.ats-scoring.enabled=true",
                    "job-agent.ats-scoring.base-url=http://ats-worker:8091",
                    "job-agent.ats-scoring.token=test-token",
                    "job-agent.ats-scoring.timeout=45s",
                    "job-agent.ats-scoring.max-document-characters=12000");

    @Test
    void bindsFromTheJobAgentConfigurationTree() {
        context.run(application -> {
            AtsScoringProperties properties = application.getBean(AtsScoringProperties.class);
            assertThat(properties.enabled()).isTrue();
            assertThat(properties.baseUrl()).isEqualTo("http://ats-worker:8091");
            assertThat(properties.token()).isEqualTo("test-token");
            assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(45));
            assertThat(properties.maxDocumentCharacters()).isEqualTo(12_000);
        });
    }
}
