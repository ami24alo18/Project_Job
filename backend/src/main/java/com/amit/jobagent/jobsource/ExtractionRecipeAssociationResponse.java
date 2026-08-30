package com.amit.jobagent.jobsource;

public record ExtractionRecipeAssociationResponse(
        JobSourceConfigurationResponse source,
        String webhookUrl,
        String webhookToken) {}
