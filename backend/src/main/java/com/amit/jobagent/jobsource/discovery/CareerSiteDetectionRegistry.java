package com.amit.jobagent.jobsource.discovery;

import com.amit.jobagent.common.error.CareerSiteDiscoveryException;
import com.amit.jobagent.jobsource.JobSourceConnectorType;
import com.amit.jobagent.jobsource.JobSourceSupportStatus;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class CareerSiteDetectionRegistry {
    static final String DETECTION_VERSION = "career-site-detection-v1";
    private static final Pattern ORACLE_SITE_PATH = Pattern.compile("(?i)(?:^|/)sites/([^/?#]+)");
    private static final Pattern JSON_LD_JOB = Pattern.compile("(?is)\\\"@type\\\"\\s*:\\s*\\\"JobPosting\\\"");
    private final CareerSiteDiscoveryProperties properties;

    CareerSiteDetectionRegistry(CareerSiteDiscoveryProperties properties) {
        this.properties = properties;
    }

    CareerSiteDiscoveryResponse detect(CareerSiteDocument source) {
        Document document = Jsoup.parse(source.markup(), source.canonicalUri().toASCIIString());
        if (document.getAllElements().size() > properties.maximumMarkupNodes()) {
            throw new CareerSiteDiscoveryException(HttpStatus.UNPROCESSABLE_ENTITY, "MARKUP_NODE_LIMIT_EXCEEDED",
                    "The career site markup contains too many elements to inspect safely");
        }
        URI canonical = source.canonicalUri();
        Optional<DetectedEndpoint> oracle = oracle(canonical).or(() -> linkedEndpoint(document, this::oracle));
        if (oracle.isPresent()) {
            return response(canonical, JobSourceConnectorType.ORACLE_CX, oracle.get().identifier(),
                    JobSourceSupportStatus.NEEDS_AUTHORIZATION,
                    "The detected Oracle integration requires an approved public interface before activation");
        }
        Optional<DetectedEndpoint> workday = workday(canonical).or(() -> linkedEndpoint(document, this::workday));
        if (workday.isPresent()) {
            return response(canonical, JobSourceConnectorType.WORKDAY, workday.get().identifier(),
                    JobSourceSupportStatus.NEEDS_AUTHORIZATION,
                    "The detected Workday site requires an approved interface before activation");
        }
        Optional<DetectedEndpoint> smartRecruiters = smartRecruiters(canonical)
                .or(() -> linkedEndpoint(document, this::smartRecruiters));
        if (smartRecruiters.isPresent()) {
            return response(canonical, JobSourceConnectorType.SMARTRECRUITERS, smartRecruiters.get().identifier(),
                    JobSourceSupportStatus.SUPPORTED,
                    "The public SmartRecruiters Posting API is supported");
        }
        boolean jobPostingJsonLd = document.select("script[type=application/ld+json]").stream()
                .map(element -> element.data().isBlank() ? element.html() : element.data())
                .anyMatch(value -> JSON_LD_JOB.matcher(value).find());
        if (jobPostingJsonLd) {
            return response(canonical, JobSourceConnectorType.GENERIC_JSON_LD, canonical.getHost(),
                    JobSourceSupportStatus.SUPPORTED,
                    "This individual job page can be imported through its JobPosting structured data");
        }
        String contentType = source.contentType() == null ? "" : source.contentType().toLowerCase(Locale.ROOT);
        if (!contentType.isBlank() && !contentType.contains("html") && document.text().isBlank()) {
            return response(canonical, null, canonical.getHost(), JobSourceSupportStatus.UNSUPPORTED,
                    "The URL did not return an inspectable career-site page");
        }
        return response(canonical, JobSourceConnectorType.CUSTOM_RECIPE, canonical.getHost(),
                JobSourceSupportStatus.NEEDS_EXTRACTION_RECIPE,
                "No supported public ATS interface was detected; a reviewed isolated extraction recipe is required");
    }

    private Optional<DetectedEndpoint> linkedEndpoint(
            Document document,
            java.util.function.Function<URI, Optional<DetectedEndpoint>> detector) {
        for (var element : document.select("a[href],link[href],script[src],form[action]")) {
            String attribute = element.hasAttr("href") ? "href" : element.hasAttr("src") ? "src" : "action";
            String absolute = element.absUrl(attribute);
            if (absolute.isBlank() || absolute.length() > 2_000) continue;
            try {
                Optional<DetectedEndpoint> detected = detector.apply(new URI(absolute));
                if (detected.isPresent()) return detected;
            } catch (URISyntaxException ignored) {
                // A malformed page link is untrusted evidence and is simply ignored.
            }
        }
        return Optional.empty();
    }

    private Optional<DetectedEndpoint> oracle(URI uri) {
        String host = lower(uri.getHost());
        var matcher = ORACLE_SITE_PATH.matcher(uri.getPath() == null ? "" : uri.getPath());
        if (matcher.find() && (host.contains("oraclecloud.com") || uri.getPath().toLowerCase(Locale.ROOT).contains("/sites/"))) {
            return Optional.of(new DetectedEndpoint(matcher.group(1)));
        }
        return Optional.empty();
    }

    private Optional<DetectedEndpoint> workday(URI uri) {
        String host = lower(uri.getHost());
        if (host.equals("myworkdayjobs.com") || host.endsWith(".myworkdayjobs.com")) {
            return Optional.of(new DetectedEndpoint(host));
        }
        return Optional.empty();
    }

    private Optional<DetectedEndpoint> smartRecruiters(URI uri) {
        String host = lower(uri.getHost());
        if (host.equals("smartrecruiters.com") || host.endsWith(".smartrecruiters.com")) {
            String[] segments = (uri.getPath() == null ? "" : uri.getPath()).split("/");
            String identifier = segments.length > 1 && !segments[1].isBlank() ? segments[1] : host;
            return Optional.of(new DetectedEndpoint(identifier));
        }
        return Optional.empty();
    }

    private static CareerSiteDiscoveryResponse response(
            URI canonical,
            JobSourceConnectorType connector,
            String identifier,
            JobSourceSupportStatus status,
            String message) {
        return new CareerSiteDiscoveryResponse(canonical.toASCIIString(), canonical.getHost(), connector,
                identifier, status, message, DETECTION_VERSION);
    }

    private static String lower(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }
    private record DetectedEndpoint(String identifier) {}
}
