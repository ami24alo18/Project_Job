package com.amit.jobagent.jobsource;

import com.amit.jobagent.common.config.JobAgentProperties;
import com.amit.jobagent.common.error.InvalidWebhookSecretException;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.bind.annotation.*;

@RestController@RequestMapping("/api/v1/job-sources/email-alert/events")
public class EmailAlertWebhookController{
    private final EmailAlertIngestionService service;private final JobAgentProperties properties;
    public EmailAlertWebhookController(EmailAlertIngestionService service,JobAgentProperties properties){this.service=service;this.properties=properties;}
    @PostMapping public EmailAlertEventResponse ingest(@RequestHeader("X-N8N-WEBHOOK-SECRET")String supplied,@Valid@RequestBody EmailAlertEventRequest request){if(!MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8),properties.n8n().webhookSecret().getBytes(StandardCharsets.UTF_8)))throw new InvalidWebhookSecretException();return service.ingest(request);}
}
