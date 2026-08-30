package com.amit.jobagent.jobsource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ExtractionRecipeAssociationRequest(
        @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,79}") String recipeId,
        Boolean enabled) {}
