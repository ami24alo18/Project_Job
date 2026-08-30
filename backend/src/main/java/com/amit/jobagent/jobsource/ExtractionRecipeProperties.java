package com.amit.jobagent.jobsource;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "job-agent.extraction-recipes")
public record ExtractionRecipeProperties(boolean enabled, List<Definition> definitions) {
    public ExtractionRecipeProperties { definitions = definitions == null ? List.of() : List.copyOf(definitions); }

    public record Definition(
            String id,
            String version,
            String canonicalHost,
            List<String> allowedPathPrefixes,
            boolean enabled) {
        public Definition { allowedPathPrefixes = allowedPathPrefixes == null ? List.of() : List.copyOf(allowedPathPrefixes); }
    }
}
