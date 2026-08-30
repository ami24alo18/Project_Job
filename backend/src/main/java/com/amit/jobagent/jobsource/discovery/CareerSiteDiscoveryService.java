package com.amit.jobagent.jobsource.discovery;

import com.amit.jobagent.audit.AuditEventType;
import com.amit.jobagent.audit.AuditService;
import com.amit.jobagent.common.error.CareerSiteDiscoveryException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CareerSiteDiscoveryService {
    private static final int MAXIMUM_RATE_LIMIT_KEYS = 2_000;
    private static final int MAXIMUM_CACHE_ENTRIES = 1_000;
    private final CareerSiteUrlGuard guard;
    private final GuardedCareerSiteClient client;
    private final CareerSiteDetectionRegistry detector;
    private final CareerSiteDiscoveryProperties properties;
    private final AuditService audit;
    private final Clock clock;
    private final Semaphore concurrency;
    private final ConcurrentHashMap<String, ArrayDeque<Instant>> requests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedDetection> cache = new ConcurrentHashMap<>();

    @Autowired
    public CareerSiteDiscoveryService(
            CareerSiteUrlGuard guard,
            GuardedCareerSiteClient client,
            CareerSiteDetectionRegistry detector,
            CareerSiteDiscoveryProperties properties,
            AuditService audit) {
        this(guard, client, detector, properties, audit, Clock.systemUTC());
    }

    CareerSiteDiscoveryService(
            CareerSiteUrlGuard guard,
            GuardedCareerSiteClient client,
            CareerSiteDetectionRegistry detector,
            CareerSiteDiscoveryProperties properties,
            AuditService audit,
            Clock clock) {
        this.guard = guard;
        this.client = client;
        this.detector = detector;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
        this.concurrency = new Semaphore(properties.maximumConcurrentRequests(), true);
    }

    public CareerSiteDiscoveryResponse discover(CareerSiteDiscoveryRequest request, String operator) {
        if (!properties.enabled()) {
            throw unavailable("DISCOVERY_DISABLED", "Career-site discovery is disabled");
        }
        URI canonical = guard.canonicalize(request.careerSiteUrl());
        rateLimit(operator == null ? "unknown" : operator, canonical.getHost());
        String key = canonical.toASCIIString();
        Instant now = clock.instant();
        CachedDetection cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            audit(cached.response());
            return cached.response();
        }
        if (!concurrency.tryAcquire()) {
            throw unavailable("DISCOVERY_BUSY", "Career-site discovery is at its concurrency limit");
        }
        try {
            CareerSiteDiscoveryResponse response = detector.detect(client.fetch(canonical));
            cacheExpiredDetections(now);
            if (cache.size() < MAXIMUM_CACHE_ENTRIES || cache.containsKey(key)) {
                cache.put(key, new CachedDetection(response, now.plusSeconds(properties.cacheTtlSeconds())));
            }
            audit(response);
            return response;
        } finally {
            concurrency.release();
        }
    }

    private void rateLimit(String operator, String host) {
        String key = operator + '|' + host;
        Instant cutoff = clock.instant().minus(Duration.ofMinutes(1));
        if (!requests.containsKey(key) && requests.size() >= MAXIMUM_RATE_LIMIT_KEYS) {
            pruneRateLimitWindows(cutoff);
            if (requests.size() >= MAXIMUM_RATE_LIMIT_KEYS) {
                throw new CareerSiteDiscoveryException(HttpStatus.TOO_MANY_REQUESTS, "DISCOVERY_RATE_LIMITED",
                        "The discovery request limit is currently in effect");
            }
        }
        ArrayDeque<Instant> window = requests.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && !window.peekFirst().isAfter(cutoff)) window.removeFirst();
            if (window.size() >= properties.maximumRequestsPerHostPerMinute()) {
                throw new CareerSiteDiscoveryException(HttpStatus.TOO_MANY_REQUESTS, "DISCOVERY_RATE_LIMITED",
                        "Too many discovery requests were made for this career-site host");
            }
            window.addLast(clock.instant());
        }
    }

    private void pruneRateLimitWindows(Instant cutoff) {
        requests.entrySet().removeIf(entry -> {
            ArrayDeque<Instant> window = entry.getValue();
            synchronized (window) {
                while (!window.isEmpty() && !window.peekFirst().isAfter(cutoff)) window.removeFirst();
                return window.isEmpty();
            }
        });
    }

    private void cacheExpiredDetections(Instant now) {
        if (cache.size() >= MAXIMUM_CACHE_ENTRIES) {
            cache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        }
    }

    private void audit(CareerSiteDiscoveryResponse response) {
        String connector = response.connectorType() == null ? "null" : '"' + response.connectorType().name() + '"';
        audit.record(AuditEventType.JOB_SOURCE_DISCOVERED, "CareerSiteDiscovery", UUID.randomUUID(),
                "{\"connectorType\":" + connector + ",\"supportStatus\":\"" + response.supportStatus()
                        + "\",\"detectionVersion\":\"" + response.detectionVersion() + "\"}");
    }

    private static CareerSiteDiscoveryException unavailable(String code, String message) {
        return new CareerSiteDiscoveryException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }

    private record CachedDetection(CareerSiteDiscoveryResponse response, Instant expiresAt) {}
}
