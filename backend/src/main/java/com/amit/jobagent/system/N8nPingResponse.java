package com.amit.jobagent.system;

import java.time.Instant;

public record N8nPingResponse(String status, String source, Instant timestamp) {}
