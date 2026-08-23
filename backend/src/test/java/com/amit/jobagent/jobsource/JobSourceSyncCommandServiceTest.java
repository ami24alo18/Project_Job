package com.amit.jobagent.jobsource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amit.jobagent.common.error.ConflictException;
import com.amit.jobagent.common.error.ExternalSourceUnavailableException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

class JobSourceSyncCommandServiceTest {
    @Test
    void syncEnabledReturnsAnAlreadyActiveRunInsteadOfSilentlyOmittingTheSource() {
        var persistence = mock(JobSourceRunPersistenceService.class);
        var sources = mock(JobSourceConfigurationService.class);
        var worker = mock(JobSourceSyncWorker.class);
        var executor = mock(Executor.class);
        var source = mock(JobSourceConfiguration.class);
        var sourceId = UUID.randomUUID();
        var runId = UUID.randomUUID();
        var active = new JobSourceRunResponse(
                runId,
                sourceId,
                JobSourceTriggerType.N8N,
                JobSourceRunStatus.RUNNING,
                null,
                Instant.parse("2026-08-22T10:00:00Z"),
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                null,
                Instant.parse("2026-08-22T09:59:59Z"),
                List.of());
        when(source.getId()).thenReturn(sourceId);
        when(sources.enabledRemote()).thenReturn(List.of(source));
        when(persistence.queue(sourceId, JobSourceTriggerType.N8N))
                .thenThrow(new ConflictException("A synchronization run is already active for this source"));
        when(persistence.active(sourceId)).thenReturn(Optional.of(active));
        var service = new JobSourceSyncCommandService(persistence, sources, worker, executor);

        var response = service.queueEnabled();

        assertThat(response.runs()).containsExactly(new SyncRunResponse(runId, JobSourceRunStatus.RUNNING));
        verify(persistence).active(sourceId);
    }

    @Test
    void persistsFailedRunWhenBoundedExecutorRejectsSubmission() {
        var persistence = mock(JobSourceRunPersistenceService.class);
        var sources = mock(JobSourceConfigurationService.class);
        var worker = mock(JobSourceSyncWorker.class);
        var executor = mock(Executor.class);
        var sourceId = UUID.randomUUID();
        var runId = UUID.randomUUID();
        var queued = new JobSourceRunResponse(
                runId,
                sourceId,
                JobSourceTriggerType.MANUAL,
                JobSourceRunStatus.QUEUED,
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                null,
                Instant.parse("2026-08-22T10:00:00Z"),
                List.of());
        when(persistence.queue(sourceId, JobSourceTriggerType.MANUAL)).thenReturn(queued);
        doThrow(new RejectedExecutionException("test saturation")).when(executor).execute(any(Runnable.class));
        var service = new JobSourceSyncCommandService(persistence, sources, worker, executor);

        assertThatThrownBy(() -> service.queue(sourceId, JobSourceTriggerType.MANUAL))
                .isInstanceOf(ExternalSourceUnavailableException.class)
                .hasMessageContaining("temporarily full");

        verify(persistence).fail(runId, "EXECUTOR_SATURATED", "The synchronization worker queue is full");
    }
}
