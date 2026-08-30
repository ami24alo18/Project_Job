package com.amit.jobagent.jobsource.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amit.jobagent.common.error.CareerSiteDiscoveryException;
import com.amit.jobagent.common.error.DomainValidationException;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class GuardedCareerSiteClientTest {
    @Test
    void resolvesAndValidatesAgainForEveryRedirectToPreventDnsRebinding() throws Exception {
        var resolution = new AtomicInteger();
        CareerSiteDnsResolver resolver = host -> resolution.getAndIncrement() == 0
                ? List.of(InetAddress.getByName("93.184.216.34"))
                : List.of(InetAddress.getByName("10.0.0.7"));
        var properties = CareerSiteDiscoveryTestSupport.properties();
        var guard = new CareerSiteUrlGuard(resolver, properties);
        var calls = new AtomicInteger();
        CareerSiteHttpTransport transport = target -> {
            calls.incrementAndGet();
            return new CareerSiteHttpResponse(302, "/next", "text/html", null, new byte[0]);
        };

        var client = new GuardedCareerSiteClient(guard, transport, properties);

        assertThatThrownBy(() -> client.fetch(URI.create("https://careers.example.com/start")))
                .isInstanceOf(DomainValidationException.class);
        assertThat(calls).hasValue(1);
        assertThat(resolution).hasValue(2);
    }

    @Test
    void rejectsSchemeDowngradeAndLiteralIpRedirects() throws Exception {
        var properties = CareerSiteDiscoveryTestSupport.properties();
        var guard = new CareerSiteUrlGuard(host -> List.of(InetAddress.getByName("93.184.216.34")), properties);

        for (String location : List.of("http://careers.example.com/jobs", "https://127.0.0.1/jobs")) {
            CareerSiteHttpTransport transport = target -> new CareerSiteHttpResponse(
                    302, location, "text/html", null, new byte[0]);
            var client = new GuardedCareerSiteClient(guard, transport, properties);
            assertThatThrownBy(() -> client.fetch(URI.create("https://careers.example.com/start")))
                    .isInstanceOf(DomainValidationException.class);
        }
    }

    @Test
    void rejectsRedirectLoopsAndExcessHops() throws Exception {
        var properties = CareerSiteDiscoveryTestSupport.properties(true, 1, 1_024, 2_048, 20, 10_000, 1_000, 2, 10, 300);
        var guard = new CareerSiteUrlGuard(host -> List.of(InetAddress.getByName("93.184.216.34")), properties);
        CareerSiteHttpTransport loop = target -> new CareerSiteHttpResponse(
                302, target.uri().toASCIIString(), "text/html", null, new byte[0]);
        assertThatThrownBy(() -> new GuardedCareerSiteClient(guard, loop, properties)
                .fetch(URI.create("https://careers.example.com/start")))
                .isInstanceOf(CareerSiteDiscoveryException.class)
                .extracting(error -> ((CareerSiteDiscoveryException) error).getSafeCode())
                .isEqualTo("REDIRECT_LOOP");

        CareerSiteHttpTransport endless = target -> new CareerSiteHttpResponse(
                302, target.uri().getPath() + "-next", "text/html", null, new byte[0]);
        assertThatThrownBy(() -> new GuardedCareerSiteClient(guard, endless, properties)
                .fetch(URI.create("https://careers.example.com/start")))
                .isInstanceOf(CareerSiteDiscoveryException.class)
                .extracting(error -> ((CareerSiteDiscoveryException) error).getSafeCode())
                .isEqualTo("REDIRECT_LIMIT_EXCEEDED");
    }

    @Test
    void enforcesDecompressedByteAndCompressionRatioLimits() throws Exception {
        byte[] compressed = gzip("x".repeat(1_000));
        var ratioProperties = CareerSiteDiscoveryTestSupport.properties(true, 1, 1_024, 2_000, 2, 10_000, 1_000, 2, 10, 300);
        var ratioClient = client(ratioProperties,
                new CareerSiteHttpResponse(200, null, "text/html", "gzip", compressed));
        assertThatThrownBy(() -> ratioClient.fetch(URI.create("https://careers.example.com/jobs")))
                .isInstanceOf(CareerSiteDiscoveryException.class)
                .extracting(error -> ((CareerSiteDiscoveryException) error).getSafeCode())
                .isEqualTo("COMPRESSION_RATIO_EXCEEDED");

        var sizeProperties = CareerSiteDiscoveryTestSupport.properties(true, 1, 1_024, 100, 100, 10_000, 1_000, 2, 10, 300);
        var sizeClient = client(sizeProperties,
                new CareerSiteHttpResponse(200, null, "text/html", "gzip", compressed));
        assertThatThrownBy(() -> sizeClient.fetch(URI.create("https://careers.example.com/jobs")))
                .isInstanceOf(CareerSiteDiscoveryException.class)
                .extracting(error -> ((CareerSiteDiscoveryException) error).getSafeCode())
                .isEqualTo("DECOMPRESSED_RESPONSE_TOO_LARGE");
    }

    @Test
    void rejectsUnsupportedEncodingAndOversizedMarkup() throws Exception {
        var properties = CareerSiteDiscoveryTestSupport.properties(true, 1, 1_024, 2_048, 20, 10, 1_000, 2, 10, 300);
        assertThatThrownBy(() -> client(properties,
                new CareerSiteHttpResponse(200, null, "text/html", "br", "jobs".getBytes(StandardCharsets.UTF_8)))
                .fetch(URI.create("https://careers.example.com/jobs")))
                .isInstanceOf(CareerSiteDiscoveryException.class)
                .extracting(error -> ((CareerSiteDiscoveryException) error).getSafeCode())
                .isEqualTo("UNSUPPORTED_CONTENT_ENCODING");

        assertThatThrownBy(() -> client(properties,
                new CareerSiteHttpResponse(200, null, "text/html", null, "a".repeat(11).getBytes(StandardCharsets.UTF_8)))
                .fetch(URI.create("https://careers.example.com/jobs")))
                .isInstanceOf(CareerSiteDiscoveryException.class)
                .extracting(error -> ((CareerSiteDiscoveryException) error).getSafeCode())
                .isEqualTo("RESPONSE_TOO_LARGE");
    }

    private static GuardedCareerSiteClient client(
            CareerSiteDiscoveryProperties properties,
            CareerSiteHttpResponse response) throws Exception {
        var guard = new CareerSiteUrlGuard(host -> List.of(InetAddress.getByName("93.184.216.34")), properties);
        return new GuardedCareerSiteClient(guard, target -> response, properties);
    }

    private static byte[] gzip(String text) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(output)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return output.toByteArray();
    }
}
