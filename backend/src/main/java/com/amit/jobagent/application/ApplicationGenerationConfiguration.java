package com.amit.jobagent.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        ContentGenerationProperties.class,
        OllamaContentGenerationProperties.class,
        OpenRouterContentGenerationProperties.class,
        HuggingFaceContentGenerationProperties.class,
        AtsScoringProperties.class
})
class ApplicationGenerationConfiguration {}
