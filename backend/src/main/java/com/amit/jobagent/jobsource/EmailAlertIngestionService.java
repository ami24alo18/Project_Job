package com.amit.jobagent.jobsource;

import com.amit.jobagent.audit.AuditEventType;
import com.amit.jobagent.audit.AuditService;
import com.amit.jobagent.common.error.ResourceNotFoundException;
import com.amit.jobagent.job.JobCandidate;
import com.amit.jobagent.job.JobPostingService;
import com.amit.jobagent.job.JobSourceType;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class EmailAlertIngestionService {
    private final EmailIngestionEventRepository events;
    private final JobSourceConfigurationService sources;
    private final JobPostingService jobs;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate transactions;

    EmailAlertIngestionService(
            EmailIngestionEventRepository events,
            JobSourceConfigurationService sources,
            JobPostingService jobs,
            AuditService audit,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.events = events;
        this.sources = sources;
        this.jobs = jobs;
        this.audit = audit;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    EmailAlertEventResponse ingest(EmailAlertEventRequest request) {
        var messageId = request.messageId().trim();
        var replay = transactions.execute(status -> events.findByMessageId(messageId)
                .map(event -> response(event, true, List.of()))
                .orElse(null));
        if (replay != null) return replay;

        var source = sources.emailSource(request.sourceName().trim());
        UUID eventId;
        try {
            eventId = transactions.execute(status -> events.saveAndFlush(new EmailIngestionEvent(
                    messageId, request.provider(), source.getId(), request.receivedAt(), Instant.now(clock))).id());
        } catch (DataIntegrityViolationException concurrentReplay) {
            var existing = transactions.execute(status -> events.findByMessageId(messageId).orElse(null));
            if (existing != null) return response(existing, true, List.of());
            throw concurrentReplay;
        }

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int duplicates = 0;
        int failed = 0;
        var results = new ArrayList<EmailAlertJobResult>();
        for (int index = 0; index < request.jobs().size(); index++) {
            var item = request.jobs().get(index);
            var externalId = item.externalId() == null || item.externalId().isBlank()
                    ? "email:" + messageId + ":" + index
                    : item.externalId().trim();
            try {
                var outcome = jobs.ingest(candidate(source, item, externalId), null);
                switch (outcome.action()) {
                    case CREATED -> created++;
                    case UPDATED -> updated++;
                    case UNCHANGED -> unchanged++;
                    case DUPLICATE -> duplicates++;
                }
                results.add(new EmailAlertJobResult(index, externalId, outcome.jobId(), outcome.action(), null, null));
            } catch (Exception invalidJob) {
                failed++;
                results.add(new EmailAlertJobResult(index, externalId, null, null,
                        "INVALID_JOB", "The mapped job could not be normalized or stored"));
            }
        }

        int finalCreated = created;
        int finalUpdated = updated;
        int finalUnchanged = unchanged;
        int finalDuplicates = duplicates;
        int finalFailed = failed;
        return transactions.execute(status -> {
            var event = events.findById(eventId)
                    .orElseThrow(() -> new ResourceNotFoundException("Email ingestion event was not found"));
            event.finish(request.jobs().size(), finalCreated, finalUpdated, finalUnchanged, finalDuplicates, finalFailed);
            events.saveAndFlush(event);
            audit.record(AuditEventType.EMAIL_ALERT_INGESTED, "EmailIngestionEvent", event.id(),
                    "{\"jobCount\":" + request.jobs().size() + ",\"failedCount\":" + finalFailed + "}");
            return response(event, false, results);
        });
    }

    private static JobCandidate candidate(JobSourceConfiguration source, EmailAlertJobRequest request, String externalId) {
        return new JobCandidate(source.getId(), JobSourceType.EMAIL_WEBHOOK, externalId,
                request.company(), request.title(), request.location(), request.countryCode(),
                request.workplaceType(), request.employmentType(), request.department(), request.team(),
                request.description(), true, request.applyUrl(), request.sourceUrl(),
                request.salaryMinimum(), request.salaryMaximum(), request.salaryCurrency(), request.salaryInterval(),
                request.publishedAt(), null, request.expiresAt());
    }

    private static EmailAlertEventResponse response(
            EmailIngestionEvent event, boolean replayed, List<EmailAlertJobResult> results) {
        return new EmailAlertEventResponse(event.id(), event.messageId(), replayed, event.status(), event.jobCount(),
                event.createdCount(), event.updatedCount(), event.unchangedCount(), event.duplicateCount(),
                event.failedCount(), results);
    }
}
