package com.amit.jobagent.jobsource.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amit.jobagent.audit.AuditService;
import com.amit.jobagent.common.error.CareerSiteDiscoveryException;
import com.amit.jobagent.jobsource.JobSourceConnectorType;
import com.amit.jobagent.jobsource.JobSourceSupportStatus;
import java.net.InetAddress;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CareerSiteDiscoveryServiceTest {
    @Test
    void cachesSafeDetectionWithoutCachingMarkupAndAuditsEachUse() throws Exception {
        var properties = CareerSiteDiscoveryTestSupport.properties();
        var guard = guard(properties);
        var client = mock(GuardedCareerSiteClient.class);
        var detector = mock(CareerSiteDetectionRegistry.class);
        var audit = mock(AuditService.class);
        var document = new CareerSiteDocument(URI.create("https://careers.example.com/jobs"), "text/html", "private markup");
        var response = response();
        when(client.fetch(any())).thenReturn(document);
        when(detector.detect(document)).thenReturn(response);
        var service = new CareerSiteDiscoveryService(guard, client, detector, properties, audit,
                Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC));
        var request = new CareerSiteDiscoveryRequest("Example", "https://careers.example.com/jobs");

        assertThat(service.discover(request, "owner")).isEqualTo(response);
        assertThat(service.discover(request, "owner")).isEqualTo(response);

        verify(client, times(1)).fetch(any());
        verify(detector, times(1)).detect(document);
        verify(audit, times(2)).record(any(), any(), any(), any());
    }

    @Test
    void enforcesPerOperatorHostRateLimitBeforeUsingCache() throws Exception {
        var properties = CareerSiteDiscoveryTestSupport.properties(true, 1, 1_024, 2_048, 20, 10_000, 1_000, 2, 1, 300);
        var client = mock(GuardedCareerSiteClient.class);
        var detector = mock(CareerSiteDetectionRegistry.class);
        when(client.fetch(any())).thenReturn(new CareerSiteDocument(URI.create("https://careers.example.com/jobs"), "text/html", "<html/>"));
        when(detector.detect(any())).thenReturn(response());
        var service = new CareerSiteDiscoveryService(guard(properties), client, detector, properties,
                mock(AuditService.class), Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC));
        var request = new CareerSiteDiscoveryRequest("Example", "https://careers.example.com/jobs");
        service.discover(request, "owner");

        assertThatThrownBy(() -> service.discover(request, "owner"))
                .isInstanceOf(CareerSiteDiscoveryException.class)
                .extracting(error -> ((CareerSiteDiscoveryException) error).getSafeCode())
                .isEqualTo("DISCOVERY_RATE_LIMITED");
    }

    @Test
    void enforcesGlobalConcurrentRequestLimit() throws Exception {
        var properties = CareerSiteDiscoveryTestSupport.properties(true, 1, 1_024, 2_048, 20, 10_000, 1_000, 1, 10, 300);
        var client = mock(GuardedCareerSiteClient.class);
        var detector = mock(CareerSiteDetectionRegistry.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(client.fetch(any())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return new CareerSiteDocument(invocation.getArgument(0), "text/html", "<html/>");
        });
        when(detector.detect(any())).thenReturn(response());
        var service = new CareerSiteDiscoveryService(guard(properties), client, detector, properties,
                mock(AuditService.class), Clock.systemUTC());

        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> service.discover(
                    new CareerSiteDiscoveryRequest("One", "https://one.example.com/jobs"), "owner"));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> service.discover(
                    new CareerSiteDiscoveryRequest("Two", "https://two.example.com/jobs"), "owner"))
                    .isInstanceOf(CareerSiteDiscoveryException.class)
                    .extracting(error -> ((CareerSiteDiscoveryException) error).getSafeCode())
                    .isEqualTo("DISCOVERY_BUSY");
            release.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo(response());
        }
    }

    @Test
    void honorsDiscoveryFeatureFlag() throws Exception {
        var properties = CareerSiteDiscoveryTestSupport.properties(false, 1, 1_024, 2_048, 20, 10_000, 1_000, 1, 10, 300);
        var service = new CareerSiteDiscoveryService(guard(properties), mock(GuardedCareerSiteClient.class),
                mock(CareerSiteDetectionRegistry.class), properties, mock(AuditService.class));

        assertThatThrownBy(() -> service.discover(
                new CareerSiteDiscoveryRequest("Example", "https://careers.example.com/jobs"), "owner"))
                .isInstanceOf(CareerSiteDiscoveryException.class)
                .extracting(error -> ((CareerSiteDiscoveryException) error).getSafeCode())
                .isEqualTo("DISCOVERY_DISABLED");
    }

    private static CareerSiteUrlGuard guard(CareerSiteDiscoveryProperties properties) throws Exception {
        InetAddress address = InetAddress.getByName("93.184.216.34");
        return new CareerSiteUrlGuard(host -> List.of(address), properties);
    }

    private static CareerSiteDiscoveryResponse response() {
        return new CareerSiteDiscoveryResponse("https://careers.example.com/jobs", "careers.example.com",
                JobSourceConnectorType.CUSTOM_RECIPE, "careers.example.com",
                JobSourceSupportStatus.NEEDS_EXTRACTION_RECIPE, "A reviewed recipe is required",
                CareerSiteDetectionRegistry.DETECTION_VERSION);
    }
}
