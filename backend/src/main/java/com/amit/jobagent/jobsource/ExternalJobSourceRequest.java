package com.amit.jobagent.jobsource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ExternalJobSourceRequest(
        @NotBlank @Size(max = 200) String displayName,
        @NotBlank @Size(max = 200)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,199}") String providerIdentifier,
        @NotNull JobSourceConnectorType connectorType,
        Boolean enabled) {}
