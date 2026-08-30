package com.amit.jobagent.jobsource.connector;

import com.amit.jobagent.job.JobSourceType;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Resolves only provider-owned public-feed hosts. No URL supplied by a user is
 * accepted or resolved by this class.
 */
public final class ProviderEndpointResolver {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,199}");
    private static final URI LEVER_GLOBAL = URI.create("https://api.lever.co/v0/postings/");
    private static final URI LEVER_EU = URI.create("https://api.eu.lever.co/v0/postings/");
    private static final URI GREENHOUSE = URI.create("https://boards-api.greenhouse.io/v1/boards/");
    private static final URI SMARTRECRUITERS = URI.create("https://api.smartrecruiters.com/v1/companies/");

    private final URI leverGlobal;
    private final URI leverEu;
    private final URI greenhouse;
    private final URI smartRecruiters;
    private final boolean productionAllowlist;

    public ProviderEndpointResolver() {
        this(LEVER_GLOBAL, LEVER_EU, GREENHOUSE, SMARTRECRUITERS, true);
    }

    /** Package-private seam for local WireMock contract tests only. */
    ProviderEndpointResolver(URI leverGlobal, URI leverEu, URI greenhouse) {
        this(leverGlobal, leverEu, greenhouse, SMARTRECRUITERS, false);
    }

    ProviderEndpointResolver(URI leverGlobal, URI leverEu, URI greenhouse, URI smartRecruiters) {
        this(leverGlobal, leverEu, greenhouse, smartRecruiters, false);
    }

    private ProviderEndpointResolver(URI leverGlobal, URI leverEu, URI greenhouse, URI smartRecruiters, boolean productionAllowlist) {
        this.leverGlobal = base(leverGlobal);
        this.leverEu = base(leverEu);
        this.greenhouse = base(greenhouse);
        this.smartRecruiters = base(smartRecruiters);
        this.productionAllowlist = productionAllowlist;
    }

    public URI leverPostings(String site, SourceRegion region, int skip, int limit) {
        validateIdentifier(site);
        if (skip < 0 || limit < 1) {
            throw invalidConfiguration("Lever pagination values are invalid");
        }
        URI base = switch (region == null ? SourceRegion.DEFAULT : region) {
            case DEFAULT, GLOBAL -> leverGlobal;
            case EU -> leverEu;
        };
        URI endpoint = build(base, site, "mode=json&skip=" + skip + "&limit=" + limit);
        validateResolved(endpoint, JobSourceType.LEVER, region);
        return endpoint;
    }

    public URI greenhouseJobs(String boardToken, SourceRegion region) {
        validateIdentifier(boardToken);
        SourceRegion selected = region == null ? SourceRegion.DEFAULT : region;
        if (selected != SourceRegion.DEFAULT) {
            throw invalidConfiguration("Greenhouse public job-board sources use the DEFAULT region");
        }
        URI endpoint = build(greenhouse, boardToken + "/jobs", "content=true");
        validateResolved(endpoint, JobSourceType.GREENHOUSE, selected);
        return endpoint;
    }

    public URI smartRecruitersPostings(String companyIdentifier, int offset, int limit) {
        validateIdentifier(companyIdentifier);
        if (offset < 0 || limit < 1 || limit > 100) throw invalidConfiguration("SmartRecruiters pagination values are invalid");
        URI endpoint = build(smartRecruiters, companyIdentifier + "/postings",
                "destination=PUBLIC&offset=" + offset + "&limit=" + limit);
        if (productionAllowlist && !("https".equals(endpoint.getScheme())
                && "api.smartrecruiters.com".equals(endpoint.getHost()))) {
            throw new SourceFetchException(SourceFetchErrorCode.ENDPOINT_NOT_ALLOWED,
                    "The resolved provider endpoint is not allowlisted");
        }
        return endpoint;
    }

    private void validateResolved(URI endpoint, JobSourceType sourceType, SourceRegion region) {
        if (!productionAllowlist) {
            return;
        }
        String host = endpoint.getHost();
        boolean allowed = endpoint.getScheme().equals("https") && switch (sourceType) {
            case LEVER -> (region == SourceRegion.EU ? "api.eu.lever.co" : "api.lever.co").equals(host);
            case GREENHOUSE -> "boards-api.greenhouse.io".equals(host);
            default -> false;
        };
        if (!allowed) {
            throw new SourceFetchException(SourceFetchErrorCode.ENDPOINT_NOT_ALLOWED,
                    "The resolved provider endpoint is not allowlisted");
        }
    }

    private static URI base(URI value) {
        Objects.requireNonNull(value, "provider base URI is required");
        if (!value.isAbsolute() || value.getHost() == null || value.getRawQuery() != null || value.getRawFragment() != null) {
            throw new IllegalArgumentException("Provider base URI must be an absolute HTTP endpoint without query or fragment");
        }
        String path = value.getRawPath();
        return path.endsWith("/") ? value : URI.create(value + "/");
    }

    private static URI build(URI base, String pathSuffix, String query) {
        try {
            // Identifiers are first restricted to unreserved characters. The
            // multi-argument URI constructor then encodes the complete path.
            return new URI(base.getScheme(), null, base.getHost(), base.getPort(),
                    base.getPath() + pathSuffix, query, null);
        } catch (URISyntaxException ex) {
            throw invalidConfiguration("The provider identifier cannot form a valid endpoint", ex);
        }
    }

    private static void validateIdentifier(String value) {
        String candidate = value == null ? "" : value.trim();
        if (!IDENTIFIER.matcher(candidate).matches() || candidate.equals(".") || candidate.equals("..")) {
            throw invalidConfiguration("Provider identifier must be a single safe path segment");
        }
    }

    private static SourceFetchException invalidConfiguration(String message) {
        return invalidConfiguration(message, null);
    }

    private static SourceFetchException invalidConfiguration(String message, Throwable cause) {
        return new SourceFetchException(
                SourceFetchErrorCode.INVALID_CONFIGURATION, message, null, false, null, cause);
    }
}
