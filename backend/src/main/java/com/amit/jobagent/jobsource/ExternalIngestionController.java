package com.amit.jobagent.jobsource;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/job-sources/{sourceId}/external-events")
class ExternalIngestionController {
    private final ExternalIngestionService service;

    ExternalIngestionController(ExternalIngestionService service) { this.service = service; }

    @PostMapping
    ExternalIngestionEventResponse ingest(
            @PathVariable UUID sourceId,
            @RequestHeader(value = "X-Job-Agent-Webhook-Token", required = false) String token,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ExternalIngestionEventRequest request) {
        return service.ingest(sourceId, token, idempotencyKey, request);
    }
}
