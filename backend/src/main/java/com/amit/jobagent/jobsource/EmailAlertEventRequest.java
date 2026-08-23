package com.amit.jobagent.jobsource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record EmailAlertEventRequest(@NotBlank@Size(max=300)String messageId,@NotNull EmailProvider provider,@NotNull Instant receivedAt,@NotBlank@Size(max=200)String sourceName,@NotEmpty@Size(max=100)List<@Valid EmailAlertJobRequest>jobs){}
