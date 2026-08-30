package com.amit.jobagent.jobsource;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "job-agent.jobspy")
public record JobSpyProperties(List<String> allowedSites) {
    private static final Set<String> KNOWN = Set.of(
            "indeed", "linkedin", "zip_recruiter", "glassdoor", "google", "bayt", "naukri", "bdjobs");

    public JobSpyProperties {
        allowedSites = allowedSites == null ? List.of() : allowedSites.stream()
                .map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
        if (!KNOWN.containsAll(allowedSites)) {
            throw new IllegalStateException("JOBSPY_ALLOWED_SITES contains an unsupported site");
        }
    }
}
