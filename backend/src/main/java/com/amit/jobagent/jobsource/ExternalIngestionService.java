package com.amit.jobagent.jobsource;

import com.amit.jobagent.common.error.ConflictException;
import com.amit.jobagent.common.error.DomainValidationException;
import com.amit.jobagent.job.JobCandidate;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
class ExternalIngestionService {
    private final JobSourceConfigurationService sources;
    private final JobSourceSearchRuleService searchRules;
    private final ExternalIngestionPersistenceService persistence;
    private final ExternalJobRecordIngestionService recordIngestion;
    private final ObjectMapper canonicalMapper;

    ExternalIngestionService(
            JobSourceConfigurationService sources,
            JobSourceSearchRuleService searchRules,
            ExternalIngestionPersistenceService persistence,
            ExternalJobRecordIngestionService recordIngestion,
            ObjectMapper objectMapper) {
        this.sources = sources;
        this.searchRules = searchRules;
        this.persistence = persistence;
        this.recordIngestion = recordIngestion;
        canonicalMapper = objectMapper.copy()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    ExternalIngestionEventResponse ingest(
            UUID sourceId,
            String suppliedToken,
            String idempotencyKey,
            ExternalIngestionEventRequest request) {
        var source = sources.authenticateExternal(sourceId, suppliedToken);
        if (idempotencyKey == null || !idempotencyKey.equals(request.eventId())) {
            throw new DomainValidationException("Idempotency-Key must exactly match eventId");
        }
        if (!connectorMatches(source.connectorType(), request.ingestionProvider())) {
            throw new ConflictException("The ingestion provider does not match the configured connector");
        }
        var checksum = checksum(request);
        var existing = persistence.find(sourceId, request.eventId());
        if (existing != null) return replay(existing, checksum);

        String query = request.query() == null || request.query().isBlank() ? null : request.query().trim();
        if (request.searchRuleId() != null) {
            var rule = searchRules.requireEnabled(sourceId, request.searchRuleId());
            if (query == null) query = rule.query();
        }

        ExternalIngestionStart started;
        try {
            started = persistence.begin(source, request, query, checksum);
        } catch (DataIntegrityViolationException concurrentRequest) {
            var concurrent = persistence.find(sourceId, request.eventId());
            if (concurrent == null) throw concurrentRequest;
            return replay(concurrent, checksum);
        }

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int duplicates = 0;
        int failed = 0;
        for (int index = 0; index < request.jobs().size(); index++) {
            var record = request.jobs().get(index);
            try {
                var outcome = recordIngestion.ingest(
                        started.eventDatabaseId(), started.runId(), index, record.externalId(),
                        candidate(source, request, query, record, started.eventDatabaseId()));
                switch (outcome.action()) {
                    case CREATED -> created++;
                    case UPDATED -> updated++;
                    case UNCHANGED -> unchanged++;
                    case DUPLICATE -> duplicates++;
                }
            } catch (Exception invalidRecord) {
                failed++;
                persistence.recordFailure(
                        started.eventDatabaseId(), started.runId(), index, record.externalId());
            }
        }
        var metrics = new RunMetrics(
                request.jobs().size(), created, updated, unchanged, duplicates, failed, 0);
        var status = failed == 0
                ? ExternalIngestionEventStatus.SUCCEEDED
                : failed == request.jobs().size()
                        ? ExternalIngestionEventStatus.FAILED
                        : ExternalIngestionEventStatus.PARTIAL_SUCCESS;
        return persistence.complete(started.eventDatabaseId(), started.runId(), status, metrics);
    }

    private ExternalIngestionEventResponse replay(ExternalIngestionEvent existing, String checksum) {
        if (!MessageDigest.isEqual(
                existing.payloadChecksum().getBytes(StandardCharsets.US_ASCII),
                checksum.getBytes(StandardCharsets.US_ASCII))) {
            throw new ConflictException("The event ID was already used with a different payload");
        }
        return persistence.replay(existing);
    }

    private JobCandidate candidate(
            AuthenticatedExternalSource source,
            ExternalIngestionEventRequest request,
            String query,
            ExternalJobRecordRequest record,
            UUID eventDatabaseId) {
        return new JobCandidate(
                source.id(), source.sourceType(), record.externalId(), record.company(), record.title(),
                record.location(), record.countryCode(), record.workplaceType(), record.employmentType(),
                record.department(), record.team(), record.description(), true, record.applyUrl(), record.sourceUrl(),
                record.salaryMinimum(), record.salaryMaximum(), record.salaryCurrency(), record.salaryInterval(),
                record.publishedAt(), record.sourceUpdatedAt(), record.expiresAt(),
                request.ingestionProvider().jobProvider(), record.originPublisher(), query,
                eventDatabaseId, source.extractionRecipeVersion());
    }

    private static boolean connectorMatches(
            JobSourceConnectorType connector, ExternalIngestionProvider provider) {
        return connector != null && connector.name().equals(provider.name());
    }

    private String checksum(ExternalIngestionEventRequest request) {
        try {
            var bytes = canonicalMapper.writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to checksum the external event", failure);
        }
    }
}
