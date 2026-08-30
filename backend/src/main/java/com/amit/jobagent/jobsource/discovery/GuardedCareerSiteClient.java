package com.amit.jobagent.jobsource.discovery;

import com.amit.jobagent.common.error.CareerSiteDiscoveryException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class GuardedCareerSiteClient {
    private final CareerSiteUrlGuard guard;
    private final CareerSiteHttpTransport transport;
    private final CareerSiteDiscoveryProperties properties;

    GuardedCareerSiteClient(
            CareerSiteUrlGuard guard,
            CareerSiteHttpTransport transport,
            CareerSiteDiscoveryProperties properties) {
        this.guard = guard;
        this.transport = transport;
        this.properties = properties;
    }

    CareerSiteDocument fetch(URI initialUri) {
        URI current = guard.canonicalize(initialUri.toASCIIString());
        Set<String> visited = new HashSet<>();
        for (int redirectCount = 0; ; redirectCount++) {
            if (!visited.add(current.toASCIIString())) {
                throw failure("REDIRECT_LOOP", "The career site returned a redirect loop");
            }
            var target = guard.validateAndResolve(current);
            var response = transport.get(target);
            if (isRedirect(response.status())) {
                if (redirectCount >= properties.maximumRedirects()) {
                    throw failure("REDIRECT_LIMIT_EXCEEDED", "The career site exceeded the redirect limit");
                }
                if (response.location() == null || response.location().isBlank()) {
                    throw failure("INVALID_REDIRECT", "The career site returned a redirect without a valid destination");
                }
                current = redirected(current, response.location());
                continue;
            }
            if (response.status() < 200 || response.status() > 299) {
                throw failure("REMOTE_HTTP_ERROR", "The career site did not return a successful response");
            }
            String markup = decode(response.body(), response.contentEncoding());
            if (markup.length() > properties.maximumMarkupCharacters()) {
                throw failure("RESPONSE_TOO_LARGE", "The career site markup exceeds the configured size limit");
            }
            return new CareerSiteDocument(current, bounded(response.contentType(), 200), markup);
        }
    }

    private URI redirected(URI current, String rawLocation) {
        if (rawLocation.length() > 2_000 || rawLocation.chars().anyMatch(Character::isISOControl)) {
            throw failure("INVALID_REDIRECT", "The career site returned an invalid redirect destination");
        }
        try {
            return guard.canonicalize(current.resolve(new URI(rawLocation)).toASCIIString());
        } catch (IllegalArgumentException | URISyntaxException ex) {
            throw failure("INVALID_REDIRECT", "The career site returned an invalid redirect destination");
        }
    }

    private String decode(byte[] compressed, String rawEncoding) {
        String encoding = rawEncoding == null ? "identity" : rawEncoding.trim().toLowerCase(Locale.ROOT);
        if (encoding.isBlank()) encoding = "identity";
        byte[] decoded;
        if (encoding.equals("identity")) {
            decoded = compressed;
        } else if (encoding.equals("gzip") || encoding.equals("x-gzip")) {
            try (var gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
                    var output = new ByteArrayOutputStream(Math.min(properties.maximumDecompressedBytes(), 16_384))) {
                gzip.transferTo(new BoundedOutputStream(output, properties.maximumDecompressedBytes()));
                decoded = output.toByteArray();
            } catch (ResponseLimitExceededException ex) {
                throw failure("DECOMPRESSED_RESPONSE_TOO_LARGE", "The career site response expands beyond the configured size limit");
            } catch (IOException ex) {
                throw failure("INVALID_COMPRESSION", "The career site returned invalid compressed content");
            }
            long permittedByRatio = Math.max(1L, compressed.length) * properties.maximumCompressionRatio();
            if (decoded.length > permittedByRatio) {
                throw failure("COMPRESSION_RATIO_EXCEEDED", "The career site response exceeds the allowed compression ratio");
            }
        } else {
            throw failure("UNSUPPORTED_CONTENT_ENCODING", "The career site returned an unsupported content encoding");
        }
        if (decoded.length > properties.maximumDecompressedBytes()) {
            throw failure("DECOMPRESSED_RESPONSE_TOO_LARGE", "The career site response expands beyond the configured size limit");
        }
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static String bounded(String value, int maximum) {
        if (value == null) return null;
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static CareerSiteDiscoveryException failure(String code, String message) {
        return new CareerSiteDiscoveryException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    private static final class BoundedOutputStream extends java.io.OutputStream {
        private final ByteArrayOutputStream delegate;
        private final int maximumBytes;

        private BoundedOutputStream(ByteArrayOutputStream delegate, int maximumBytes) {
            this.delegate = delegate;
            this.maximumBytes = maximumBytes;
        }

        @Override public void write(int value) {
            ensureCapacity(1);
            delegate.write(value);
        }

        @Override public void write(byte[] values, int offset, int length) {
            ensureCapacity(length);
            delegate.write(values, offset, length);
        }

        private void ensureCapacity(int increment) {
            if ((long) delegate.size() + increment > maximumBytes) throw new ResponseLimitExceededException();
        }
    }

    private static final class ResponseLimitExceededException extends RuntimeException {}
}
