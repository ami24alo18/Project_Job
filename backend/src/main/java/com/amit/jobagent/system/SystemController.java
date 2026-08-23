package com.amit.jobagent.system;

import com.amit.jobagent.common.config.JobAgentProperties;
import com.amit.jobagent.common.error.InvalidWebhookSecretException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
    private final JobAgentProperties properties;
    private final Clock clock;

    public SystemController(JobAgentProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @GetMapping("/health")
    public SystemHealthResponse health() {
        return new SystemHealthResponse("UP", "job-agent-backend", Instant.now(clock));
    }

    @PostMapping("/n8n/ping")
    @ResponseStatus(HttpStatus.OK)
    public N8nPingResponse ping(@RequestHeader("X-N8N-WEBHOOK-SECRET") String suppliedSecret) {
        byte[] supplied = suppliedSecret.getBytes(StandardCharsets.UTF_8);
        byte[] expected = properties.n8n().webhookSecret().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(supplied, expected)) {
            throw new InvalidWebhookSecretException();
        }
        return new N8nPingResponse("ACKNOWLEDGED", "n8n", Instant.now(clock));
    }
}
