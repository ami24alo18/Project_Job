package com.amit.jobagent.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class JobIngestionConfigurationTest {
    private ThreadPoolTaskExecutor executor;

    @AfterEach
    void cleanUp() {
        MDC.clear();
        if (executor != null) executor.shutdown();
    }

    @Test
    void propagatesSubmittingDiagnosticContextWithoutLeakingItToLaterTasks() throws Exception {
        var properties = new JobIngestionProperties(1_000, 1_000_000, 1_000, 1_000, 0, 10, 1, 1, 2, 30);
        executor = (ThreadPoolTaskExecutor) new JobIngestionConfiguration().jobSourceExecutor(properties);
        var firstValue = new AtomicReference<String>();
        var secondValue = new AtomicReference<String>();
        var firstFinished = new CountDownLatch(1);
        var secondFinished = new CountDownLatch(1);

        MDC.put("jobSourceRunId", "run-for-test");
        executor.execute(() -> {
            firstValue.set(MDC.get("jobSourceRunId"));
            firstFinished.countDown();
        });
        MDC.clear();

        assertThat(firstFinished.await(5, TimeUnit.SECONDS)).isTrue();
        executor.execute(() -> {
            secondValue.set(MDC.get("jobSourceRunId"));
            secondFinished.countDown();
        });

        assertThat(secondFinished.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(firstValue).hasValue("run-for-test");
        assertThat(secondValue.get()).isNull();
    }
}
