package com.amit.jobagent.jobsource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExternalIngestionEventRequest(
        @NotBlank @Size(max = 300)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,299}") String eventId,
        @NotNull ExternalIngestionProvider ingestionProvider,
        UUID searchRuleId,
        @Size(max = 500) String query,
        @NotNull Instant fetchedAt,
        @NotNull @Size(min = 1, max = 100) List<@Valid ExternalJobRecordRequest> jobs) {}
