package com.amit.jobagent.jobsource;

import com.amit.jobagent.common.error.ConflictException;
import com.amit.jobagent.common.error.DomainValidationException;
import com.amit.jobagent.common.error.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class JobSourceSearchRuleService {
    private final JobSourceSearchRuleRepository rules;
    private final JobSourceConfigurationService sources;

    JobSourceSearchRuleService(JobSourceSearchRuleRepository rules, JobSourceConfigurationService sources) {
        this.rules = rules;
        this.sources = sources;
    }

    @Transactional(readOnly = true)
    List<JobSourceSearchRuleResponse> list(UUID sourceId) {
        sources.requireEntity(sourceId);
        return rules.findBySourceIdOrderByCreatedAtAscIdAsc(sourceId).stream().map(JobSourceSearchRuleService::map).toList();
    }

    @Transactional
    JobSourceSearchRuleResponse create(UUID sourceId, JobSourceSearchRuleRequest request) {
        requireEditableSource(sourceId);
        var normalized = normalize(request);
        if (rules.existsBySourceIdAndNameIgnoreCase(sourceId, normalized.name())) {
            throw new ConflictException("A search rule with this name already exists for the source");
        }
        return map(rules.saveAndFlush(new JobSourceSearchRule(sourceId, normalized)));
    }

    @Transactional
    JobSourceSearchRuleResponse update(UUID sourceId, UUID ruleId, JobSourceSearchRuleRequest request) {
        requireEditableSource(sourceId);
        var rule = require(sourceId, ruleId);
        if (request.recordVersion() == null || request.recordVersion() != rule.getRecordVersion()) {
            throw new ConflictException("Search rule was updated by another request");
        }
        var normalized = normalize(request);
        if (!rule.name().equalsIgnoreCase(normalized.name())
                && rules.existsBySourceIdAndNameIgnoreCase(sourceId, normalized.name())) {
            throw new ConflictException("A search rule with this name already exists for the source");
        }
        rule.apply(normalized, false);
        return map(rules.saveAndFlush(rule));
    }

    @Transactional
    JobSourceSearchRuleResponse setEnabled(UUID sourceId, UUID ruleId, boolean enabled) {
        requireEditableSource(sourceId);
        var rule = require(sourceId, ruleId);
        rule.setEnabled(enabled);
        return map(rules.saveAndFlush(rule));
    }

    @Transactional(readOnly = true)
    JobSourceSearchRule requireEnabled(UUID sourceId, UUID ruleId) {
        var rule = require(sourceId, ruleId);
        if (!rule.enabled()) throw new ConflictException("The search rule is disabled");
        return rule;
    }

    private void requireEditableSource(UUID sourceId) {
        var source = sources.requireEntity(sourceId);
        if (source.archivedAt() != null) throw new ConflictException("Archived sources cannot manage search rules");
    }

    private JobSourceSearchRule require(UUID sourceId, UUID ruleId) {
        return rules.findByIdAndSourceId(ruleId, sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Search rule was not found"));
    }

    private static JobSourceSearchRuleRequest normalize(JobSourceSearchRuleRequest request) {
        if (!request.remoteAllowed() && !request.hybridAllowed() && !request.onsiteAllowed()) {
            throw new DomainValidationException("At least one workplace type is required");
        }
        var unique = new LinkedHashMap<String, String>();
        for (var location : request.locations()) {
            var clean = location.trim().replaceAll("\\s+", " ");
            unique.putIfAbsent(clean.toLowerCase(Locale.ROOT), clean);
        }
        return new JobSourceSearchRuleRequest(
                request.name().trim().replaceAll("\\s+", " "),
                request.query().trim().replaceAll("\\s+", " "),
                new ArrayList<>(unique.values()),
                request.remoteAllowed(), request.hybridAllowed(), request.onsiteAllowed(),
                request.datePostedWindow(), request.maximumResults(),
                request.enabled() == null || request.enabled(), request.recordVersion());
    }

    private static JobSourceSearchRuleResponse map(JobSourceSearchRule rule) {
        return new JobSourceSearchRuleResponse(
                rule.getId(), rule.sourceId(), rule.name(), rule.query(), rule.locations(),
                rule.remoteAllowed(), rule.hybridAllowed(), rule.onsiteAllowed(), rule.datePostedWindow(),
                rule.maximumResults(), rule.enabled(), rule.getRecordVersion(), rule.getCreatedAt(), rule.getUpdatedAt());
    }
}
