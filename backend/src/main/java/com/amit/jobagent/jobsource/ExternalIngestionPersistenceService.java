package com.amit.jobagent.jobsource;

import com.amit.jobagent.audit.AuditEventType;
import com.amit.jobagent.audit.AuditService;
import com.amit.jobagent.common.error.ConflictException;
import com.amit.jobagent.common.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ExternalIngestionPersistenceService {
    private final ExternalIngestionEventRepository events;
    private final ExternalIngestionEventResultRepository results;
    private final JobSourceRunRepository runs;
    private final JobSourceRunErrorRepository runErrors;
    private final JobSourceConfigurationRepository sources;
    private final AuditService audit;
    private final Clock clock;

    ExternalIngestionPersistenceService(
            ExternalIngestionEventRepository events,
            ExternalIngestionEventResultRepository results,
            JobSourceRunRepository runs,
            JobSourceRunErrorRepository runErrors,
            JobSourceConfigurationRepository sources,
            AuditService audit,
            Clock clock) {
        this.events = events;
        this.results = results;
        this.runs = runs;
        this.runErrors = runErrors;
        this.sources = sources;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    ExternalIngestionEvent find(UUID sourceId, String eventId) {
        return events.findBySourceIdAndEventId(sourceId, eventId).orElse(null);
    }

    @Transactional
    ExternalIngestionStart begin(
            AuthenticatedExternalSource source,
            ExternalIngestionEventRequest request,
            String query,
            String checksum) {
        var now = Instant.now(clock);
        var event = events.saveAndFlush(new ExternalIngestionEvent(
                source.id(), request.eventId(), request.ingestionProvider(), request.searchRuleId(), query,
                request.fetchedAt(), checksum, request.jobs().size(), now));
        var trigger = source.connectorType() == JobSourceConnectorType.CUSTOM_RECIPE
                ? JobSourceTriggerType.EXTRACTION_WORKER : JobSourceTriggerType.WEBHOOK;
        var run = new JobSourceRun(
                source.id(), trigger, JobSourceRunCoverage.PUSH_BATCH,
                request.searchRuleId(), event.id(), now);
        run.start(now);
        runs.saveAndFlush(run);
        var configuredSource = sources.findForUpdateById(source.id())
                .orElseThrow(() -> new ResourceNotFoundException("Job source was not found"));
        configuredSource.attempted(now);
        sources.saveAndFlush(configuredSource);
        audit.record(AuditEventType.JOB_SOURCE_SYNC_STARTED, "JobSourceRun", run.id(),
                "{\"sourceId\":\"" + source.id() + "\",\"triggerType\":\"" + trigger + "\"}");
        return new ExternalIngestionStart(event.id(), run.id());
    }

    @Transactional
    void recordFailure(UUID eventDatabaseId, UUID runId, int index, String externalId) {
        var now = Instant.now(clock);
        var message = "The mapped job could not be normalized or stored";
        results.saveAndFlush(ExternalIngestionEventResult.failure(
                eventDatabaseId, index, externalId, "INVALID_JOB", message, now));
        runErrors.saveAndFlush(new JobSourceRunError(runId, externalId, "INVALID_JOB", message, now));
    }

    @Transactional
    ExternalIngestionEventResponse complete(
            UUID eventDatabaseId, UUID runId, ExternalIngestionEventStatus status, RunMetrics metrics) {
        var now = Instant.now(clock);
        var event = events.findById(eventDatabaseId)
                .orElseThrow(() -> new ResourceNotFoundException("External ingestion event was not found"));
        var run = runs.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Job source run was not found"));
        var source = sources.findForUpdateById(event.sourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Job source was not found"));
        var runStatus = switch (status) {
            case SUCCEEDED -> JobSourceRunStatus.SUCCEEDED;
            case PARTIAL_SUCCESS -> JobSourceRunStatus.PARTIAL_SUCCESS;
            case FAILED -> JobSourceRunStatus.FAILED;
            case PROCESSING -> throw new IllegalArgumentException("A processing event cannot be completed");
        };
        var code = metrics.failed() == 0 ? null : "PARTIAL_RECORD_FAILURE";
        var message = metrics.failed() == 0 ? null : metrics.failed() + " job record(s) could not be processed";
        if (!run.complete(runStatus, null, metrics, now, code, message)) {
            throw new ConflictException("The external ingestion run is no longer active");
        }
        event.complete(status, metrics, now);
        if (status == ExternalIngestionEventStatus.SUCCEEDED) source.succeeded(now); else source.failed();
        events.saveAndFlush(event);
        runs.saveAndFlush(run);
        sources.saveAndFlush(source);
        audit.record(AuditEventType.JOB_SOURCE_SYNC_COMPLETED, "JobSourceRun", runId,
                "{\"status\":\"" + runStatus + "\",\"created\":" + metrics.created()
                        + ",\"updated\":" + metrics.updated() + "}");
        return response(event, run, false);
    }

    @Transactional(readOnly = true)
    ExternalIngestionEventResponse replay(ExternalIngestionEvent event) {
        if (event.status() == ExternalIngestionEventStatus.PROCESSING) {
            throw new ConflictException("The external event is still processing");
        }
        var run = runs.findByExternalEventId(event.id())
                .orElseThrow(() -> new ResourceNotFoundException("External ingestion run was not found"));
        return response(event, run, true);
    }

    private ExternalIngestionEventResponse response(
            ExternalIngestionEvent event, JobSourceRun run, boolean replayed) {
        var rows = results.findByEventIdOrderByRequestIndexAsc(event.id()).stream()
                .map(row -> new ExternalIngestionResultResponse(
                        row.requestIndex(), row.externalId(), row.jobId(), row.action(),
                        row.safeErrorCode(), row.safeErrorMessage()))
                .toList();
        return new ExternalIngestionEventResponse(
                event.eventId(), run.id(), replayed, event.status(), event.discoveredCount(),
                event.createdCount(), event.updatedCount(), event.unchangedCount(), event.duplicateCount(),
                event.failedCount(), rows);
    }
}
