package com.amit.jobagent.jobsource;

public record ExternalJobSourceCreatedResponse(
        JobSourceConfigurationResponse source,
        String webhookUrl,
        String webhookToken) {}
