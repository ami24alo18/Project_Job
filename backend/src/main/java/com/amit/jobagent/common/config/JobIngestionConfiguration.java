package com.amit.jobagent.common.config;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class JobIngestionConfiguration {
    @Bean
    HttpClient providerHttpClient(JobIngestionProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMillis()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean(name = "jobSourceExecutor")
    Executor jobSourceExecutor(JobIngestionProperties properties) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("job-source-");
        executor.setCorePoolSize(properties.executorCorePoolSize());
        executor.setMaxPoolSize(properties.executorMaximumPoolSize());
        executor.setQueueCapacity(properties.executorQueueCapacity());
        executor.setTaskDecorator(task -> {
            var submittingContext = MDC.getCopyOfContextMap();
            return () -> {
                var workerContext = MDC.getCopyOfContextMap();
                try {
                    restoreDiagnosticContext(submittingContext);
                    task.run();
                } finally {
                    restoreDiagnosticContext(workerContext);
                }
            };
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }

    private static void restoreDiagnosticContext(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
