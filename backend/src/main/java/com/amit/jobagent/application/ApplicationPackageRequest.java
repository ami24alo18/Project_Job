package com.amit.jobagent.application;import jakarta.validation.constraints.Size;import java.util.UUID;public record ApplicationPackageRequest(UUID evaluationId,@Size(max=500)String overrideReason){}
