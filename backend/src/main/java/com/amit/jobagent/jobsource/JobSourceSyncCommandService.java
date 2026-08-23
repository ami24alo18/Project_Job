package com.amit.jobagent.jobsource;

import com.amit.jobagent.common.error.ConflictException;
import com.amit.jobagent.common.error.ExternalSourceUnavailableException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
class JobSourceSyncCommandService {
    private final JobSourceRunPersistenceService persistence;
    private final JobSourceConfigurationService sources;
    private final JobSourceSyncWorker worker;
    private final Executor executor;

    JobSourceSyncCommandService(
            JobSourceRunPersistenceService persistence,
            JobSourceConfigurationService sources,
            JobSourceSyncWorker worker,
            @Qualifier("jobSourceExecutor") Executor executor) {
        this.persistence = persistence;
        this.sources = sources;
        this.worker = worker;
        this.executor = executor;
    }

    SyncRunResponse queue(UUID sourceId, JobSourceTriggerType trigger) {
        var run = persistence.queue(sourceId, trigger);
        var previous = MDC.getCopyOfContextMap();
        try {
            MDC.put("jobSourceRunId", run.id().toString());
            MDC.put("jobSourceId", sourceId.toString());
            executor.execute(() -> worker.execute(run.id()));
        } catch (RejectedExecutionException e) {
            persistence.fail(run.id(), "EXECUTOR_SATURATED", "The synchronization worker queue is full");
            throw new ExternalSourceUnavailableException(
                    "EXECUTOR_SATURATED", "The synchronization queue is temporarily full", e);
        } finally {
            restore(previous);
        }
        return new SyncRunResponse(run.id(), run.status());
    }

    SyncEnabledResponse queueEnabled() {
        var queuedOrActive = new ArrayList<SyncRunResponse>();
        for (var source : sources.enabledRemote()) {
            try {
                queuedOrActive.add(queue(source.getId(), JobSourceTriggerType.N8N));
            } catch (ConflictException overlap) {
                var active = persistence.active(source.getId()).orElseThrow(() -> overlap);
                queuedOrActive.add(new SyncRunResponse(active.id(), active.status()));
            }
        }
        return new SyncEnabledResponse(queuedOrActive);
    }

    private static void restore(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
