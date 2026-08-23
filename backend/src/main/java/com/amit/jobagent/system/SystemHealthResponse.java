package com.amit.jobagent.system;

import java.time.Instant;

public record SystemHealthResponse(String status, String service, Instant timestamp) {}
