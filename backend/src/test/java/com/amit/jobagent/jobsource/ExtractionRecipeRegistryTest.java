package com.amit.jobagent.jobsource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amit.jobagent.common.error.DomainValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractionRecipeRegistryTest {
    @Test
    void permitsOnlyReviewedExactHostAndPathScope() {
        var registry = registry(true);

        var recipe = registry.requireCompatible("example-static", "careers.example.com",
                "https://careers.example.com/jobs/engineering");

        assertThat(recipe.version()).isEqualTo("example-static-v1");
        assertThatThrownBy(() -> registry.requireCompatible("example-static", "careers.example.com",
                "https://careers.example.com/admin"))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> registry.requireCompatible("example-static", "careers.example.com",
                "https://careers.example.com/jobs-evil"))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> registry.requireCompatible("example-static", "evil.example.com",
                "https://evil.example.com/jobs"))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void featureFlagAndUnknownRecipeFailClosed() {
        assertThatThrownBy(() -> registry(false).requireCompatible("example-static", "careers.example.com",
                "https://careers.example.com/jobs"))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> registry(true).requireCompatible("unknown", "careers.example.com",
                "https://careers.example.com/jobs"))
                .isInstanceOf(DomainValidationException.class);
    }

    private static ExtractionRecipeRegistry registry(boolean enabled) {
        return new ExtractionRecipeRegistry(new ExtractionRecipeProperties(enabled, List.of(
                new ExtractionRecipeProperties.Definition("example-static", "example-static-v1",
                        "careers.example.com", List.of("/jobs"), true))));
    }
}
