package com.amit.jobagent.jobsource;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/job-sources/{sourceId}/search-rules")
@SecurityRequirement(name = "basicAuth")
class JobSourceSearchRuleController {
    private final JobSourceSearchRuleService service;

    JobSourceSearchRuleController(JobSourceSearchRuleService service) { this.service = service; }

    @GetMapping
    List<JobSourceSearchRuleResponse> list(@PathVariable UUID sourceId) { return service.list(sourceId); }

    @PostMapping
    JobSourceSearchRuleResponse create(
            @PathVariable UUID sourceId, @Valid @RequestBody JobSourceSearchRuleRequest request) {
        return service.create(sourceId, request);
    }

    @PutMapping("/{ruleId}")
    JobSourceSearchRuleResponse update(
            @PathVariable UUID sourceId, @PathVariable UUID ruleId,
            @Valid @RequestBody JobSourceSearchRuleRequest request) {
        return service.update(sourceId, ruleId, request);
    }

    @PostMapping("/{ruleId}/enable")
    JobSourceSearchRuleResponse enable(@PathVariable UUID sourceId, @PathVariable UUID ruleId) {
        return service.setEnabled(sourceId, ruleId, true);
    }

    @PostMapping("/{ruleId}/disable")
    JobSourceSearchRuleResponse disable(@PathVariable UUID sourceId, @PathVariable UUID ruleId) {
        return service.setEnabled(sourceId, ruleId, false);
    }
}
