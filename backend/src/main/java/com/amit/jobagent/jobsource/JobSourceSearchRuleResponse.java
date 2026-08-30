package com.amit.jobagent.jobsource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobSourceSearchRuleResponse(
        UUID id,
        UUID sourceId,
        String name,
        String query,
        List<String> locations,
        boolean remoteAllowed,
        boolean hybridAllowed,
        boolean onsiteAllowed,
        DatePostedWindow datePostedWindow,
        int maximumResults,
        boolean enabled,
        long recordVersion,
        Instant createdAt,
        Instant updatedAt) {}
