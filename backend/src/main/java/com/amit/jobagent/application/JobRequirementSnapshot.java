package com.amit.jobagent.application;

import java.util.UUID;

public record JobRequirementSnapshot(
        UUID id, String text, String type, String category, String matchStatus, String jobEvidence) {}
