package com.amit.jobagent.jobsource;

import java.util.UUID;

public record WebhookTokenRotationResponse(UUID sourceId, String webhookUrl, String webhookToken) {}
