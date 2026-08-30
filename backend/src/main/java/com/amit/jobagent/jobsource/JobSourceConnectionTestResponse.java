package com.amit.jobagent.jobsource;

public record JobSourceConnectionTestResponse(
        JobSourceConfigurationResponse source,
        JobSourceConnectionTestStatus status,
        int discoveredCount,
        String message) {}
