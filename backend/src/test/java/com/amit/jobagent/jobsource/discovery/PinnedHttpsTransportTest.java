package com.amit.jobagent.jobsource.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amit.jobagent.common.error.CareerSiteDiscoveryException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PinnedHttpsTransportTest {
    @Test
    void parsesBoundedContentLengthResponseAndKeepsOnlyDetectionHeaders() throws Exception {
        var transport = transport(100);
        String raw = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/html\r\n"
                + "Content-Encoding: identity\r\n"
                + "Set-Cookie: secret=never-retained\r\n"
                + "Content-Length: 5\r\n\r\nhello";

        var response = transport.readResponse(input(raw));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("text/html");
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void parsesBoundedChunkedResponse() throws Exception {
        var transport = transport(100);
        String raw = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nContent-Type: text/html\r\n\r\n"
                + "5\r\nhello\r\n6\r\n world\r\n0\r\nX-Ignored: value\r\n\r\n";

        var response = transport.readResponse(input(raw));

        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("hello world");
    }

    @Test
    void doesNotReadRedirectBodies() throws Exception {
        var transport = transport(5);
        String raw = "HTTP/1.1 302 Found\r\nLocation: https://public.example/next\r\n"
                + "Content-Length: 1000000\r\n\r\n";

        var response = transport.readResponse(input(raw));

        assertThat(response.location()).isEqualTo("https://public.example/next");
        assertThat(response.body()).isEmpty();
    }

    @Test
    void rejectsOversizedConflictingAndMalformedResponses() {
        var transport = transport(5);
        assertThatThrownBy(() -> transport.readResponse(input(
                "HTTP/1.1 200 OK\r\nContent-Length: 6\r\n\r\n123456")))
                .isInstanceOf(CareerSiteDiscoveryException.class)
                .extracting(error -> ((CareerSiteDiscoveryException) error).getSafeCode())
                .isEqualTo("RESPONSE_TOO_LARGE");
        assertThatThrownBy(() -> transport.readResponse(input(
                "HTTP/1.1 200 OK\r\nContent-Length: 1\r\nContent-Length: 2\r\n\r\nx")))
                .isInstanceOf(CareerSiteDiscoveryException.class)
                .extracting(error -> ((CareerSiteDiscoveryException) error).getSafeCode())
                .isEqualTo("INVALID_HTTP_RESPONSE");
        assertThatThrownBy(() -> transport.readResponse(input("not-http\r\n\r\n")))
                .isInstanceOf(CareerSiteDiscoveryException.class)
                .extracting(error -> ((CareerSiteDiscoveryException) error).getSafeCode())
                .isEqualTo("INVALID_HTTP_RESPONSE");
    }

    private static PinnedHttpsTransport transport(int maximumCompressedBytes) {
        var properties = CareerSiteDiscoveryTestSupport.properties(
                true, 1, maximumCompressedBytes, 1_024, 20, 10_000, 1_000, 2, 10, 300);
        return new PinnedHttpsTransport(properties);
    }

    private static ByteArrayInputStream input(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.ISO_8859_1));
    }
}
