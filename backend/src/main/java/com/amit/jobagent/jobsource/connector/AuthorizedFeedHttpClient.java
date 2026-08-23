package com.amit.jobagent.jobsource.connector;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/** Bounded GET-only transport with safe retries and no redirect following. */
final class AuthorizedFeedHttpClient {
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final HttpClient client;
    private final ConnectorHttpSettings settings;
    private final Sleeper sleeper;
    private final LongSupplier jitterMillis;
    private final Clock clock;

    AuthorizedFeedHttpClient(ConnectorHttpSettings settings) {
        this(HttpClient.newBuilder()
                        .connectTimeout(settings.connectTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                settings,
                duration -> Thread.sleep(duration.toMillis()),
                () -> ThreadLocalRandom.current().nextLong(1_000),
                Clock.systemUTC());
    }

    AuthorizedFeedHttpClient(
            HttpClient client,
            ConnectorHttpSettings settings,
            Sleeper sleeper,
            LongSupplier jitterMillis,
            Clock clock) {
        this.client = client;
        this.settings = settings;
        this.sleeper = sleeper;
        this.jitterMillis = jitterMillis;
        this.clock = clock;
    }

    byte[] get(URI endpoint) {
        Duration lastRetryAfter = null;
        for (int attempt = 1; attempt <= settings.maximumAttempts(); attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(endpoint)
                        .GET()
                        .timeout(settings.responseTimeout())
                        .header("Accept", "application/json")
                        .header("User-Agent", settings.userAgent())
                        .build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status == 200) {
                    return readBounded(response);
                }
                closeQuietly(response.body());
                if (status >= 300 && status < 400) {
                    throw failure(SourceFetchErrorCode.REDIRECT_REJECTED,
                            "Provider redirects are not followed", status, false, null, null);
                }
                if (status == 429) {
                    lastRetryAfter = retryAfter(response);
                    if (attempt < settings.maximumAttempts()) {
                        pause(attempt, lastRetryAfter);
                        continue;
                    }
                    throw failure(SourceFetchErrorCode.RATE_LIMITED,
                            "The provider rate limit remained in effect", status, true, lastRetryAfter, null);
                }
                if (status >= 500 && status <= 599) {
                    if (attempt < settings.maximumAttempts()) {
                        pause(attempt, null);
                        continue;
                    }
                    throw failure(SourceFetchErrorCode.RETRYABLE_HTTP_ERROR,
                            "The provider remained unavailable after bounded retries", status, true, null, null);
                }
                if (status >= 400 && status <= 499) {
                    throw failure(SourceFetchErrorCode.PERMANENT_HTTP_ERROR,
                            "The provider rejected the public-feed request", status, false, null, null);
                }
                throw failure(SourceFetchErrorCode.IO_ERROR,
                        "The provider returned an unsupported HTTP status", status, false, null, null);
            } catch (HttpTimeoutException ex) {
                if (attempt < settings.maximumAttempts()) {
                    pause(attempt, null);
                    continue;
                }
                throw failure(SourceFetchErrorCode.TIMEOUT,
                        "The provider did not respond before the configured timeout", null, true, null, ex);
            } catch (SourceFetchException ex) {
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw failure(SourceFetchErrorCode.INTERRUPTED,
                        "The provider request was interrupted", null, false, null, ex);
            } catch (IOException ex) {
                if (attempt < settings.maximumAttempts()) {
                    pause(attempt, null);
                    continue;
                }
                throw failure(SourceFetchErrorCode.IO_ERROR,
                        "The provider could not be reached after bounded retries", null, true, null, ex);
            }
        }
        throw failure(SourceFetchErrorCode.IO_ERROR, "The provider request failed", null, true, lastRetryAfter, null);
    }

    private byte[] readBounded(HttpResponse<InputStream> response) throws IOException {
        long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        try (InputStream body = response.body()) {
            if (declaredLength > settings.maximumResponseBytes()) {
                throw failure(SourceFetchErrorCode.RESPONSE_TOO_LARGE,
                        "The provider response exceeds the configured size limit", 200, false, null, null);
            }
            byte[] bytes = body.readNBytes(settings.maximumResponseBytes() + 1);
            if (bytes.length > settings.maximumResponseBytes()) {
                throw failure(SourceFetchErrorCode.RESPONSE_TOO_LARGE,
                        "The provider response exceeds the configured size limit", 200, false, null, null);
            }
            return bytes;
        }
    }

    private Duration retryAfter(HttpResponse<?> response) {
        String raw = response.headers().firstValue("Retry-After").orElse(null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(raw.trim());
            return bounded(Duration.ofSeconds(Math.max(0, seconds)));
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(raw.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                return bounded(Duration.between(clock.instant(), retryAt).isNegative()
                        ? Duration.ZERO : Duration.between(clock.instant(), retryAt));
            } catch (DateTimeParseException invalidDate) {
                return null;
            }
        }
    }

    private void pause(int attempt, Duration instructedDelay) {
        Duration delay = instructedDelay == null ? exponentialDelay(attempt) : bounded(instructedDelay);
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw failure(SourceFetchErrorCode.INTERRUPTED,
                    "The provider retry wait was interrupted", null, false, null, ex);
        }
    }

    private Duration exponentialDelay(int attempt) {
        long initial = settings.initialBackoff().toMillis();
        int shift = Math.min(attempt - 1, 20);
        long exponential = initial > Long.MAX_VALUE >> shift ? Long.MAX_VALUE : initial << shift;
        long base = Math.min(exponential, settings.maximumBackoff().toMillis());
        long jitterBound = Math.max(1, Math.min(base / 2 + 1, 1_000));
        long jitter = Math.floorMod(jitterMillis.getAsLong(), jitterBound);
        return bounded(Duration.ofMillis(Math.min(Long.MAX_VALUE - base, jitter) + base));
    }

    private Duration bounded(Duration delay) {
        if (delay.isNegative()) {
            return Duration.ZERO;
        }
        return delay.compareTo(settings.maximumBackoff()) > 0 ? settings.maximumBackoff() : delay;
    }

    private static SourceFetchException failure(
            SourceFetchErrorCode code,
            String message,
            Integer status,
            boolean retryable,
            Duration retryAfter,
            Throwable cause) {
        return new SourceFetchException(code, message, status, retryable, retryAfter, cause);
    }

    private static void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // The response is already being rejected; no payload is retained.
        }
    }
}
