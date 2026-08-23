package com.amit.jobagent.profile.version;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record PublishProfileRequest(@Schema(example="Verified fictional profile for August review") @NotBlank @Size(max=500)String changeReason){}
