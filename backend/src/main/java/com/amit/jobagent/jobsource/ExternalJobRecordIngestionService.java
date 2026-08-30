package com.amit.jobagent.jobsource;

import com.amit.jobagent.job.JobCandidate;
import com.amit.jobagent.job.JobIngestionOutcome;
import com.amit.jobagent.job.JobPostingService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ExternalJobRecordIngestionService {
    private final JobPostingService jobs;
    private final ExternalIngestionEventResultRepository results;
    private final Clock clock;

    ExternalJobRecordIngestionService(
            JobPostingService jobs, ExternalIngestionEventResultRepository results, Clock clock) {
        this.jobs = jobs;
        this.results = results;
        this.clock = clock;
    }

    @Transactional
    JobIngestionOutcome ingest(
            UUID eventDatabaseId,
            UUID runId,
            int index,
            String externalId,
            JobCandidate candidate) {
        var outcome = jobs.ingest(candidate, runId);
        results.saveAndFlush(ExternalIngestionEventResult.success(
                eventDatabaseId, index, externalId, outcome.jobId(), outcome.action(), Instant.now(clock)));
        return outcome;
    }
}
