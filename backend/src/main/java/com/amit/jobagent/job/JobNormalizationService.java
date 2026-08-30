package com.amit.jobagent.job;

import com.amit.jobagent.common.config.JobIngestionProperties;
import com.amit.jobagent.common.error.DomainValidationException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Service;

@Service
public class JobNormalizationService {
    private final JobUrlCanonicalizer urls;
    private final int descriptionLimit;
    private final Clock clock;

    public JobNormalizationService(JobUrlCanonicalizer urls, JobIngestionProperties properties, Clock clock) {
        this.urls = urls; this.descriptionLimit = properties.maximumDescriptionCharacters(); this.clock = clock;
    }

    public NormalizedJob normalize(JobCandidate input) {
        if (input == null || input.sourceType() == null) throw new DomainValidationException("Job source type is required");
        var company = compact(input.company());
        var title = compact(input.title());
        if (company == null || title == null) throw new DomainValidationException("Company and title are required");
        var externalId = compact(input.externalId());
        if (externalId == null) throw new DomainValidationException("External job identifier is required");
        var description = description(input.description(), input.descriptionHtml());
        int descriptionCodePoints = description.codePointCount(0, description.length());
        boolean truncated = descriptionCodePoints > descriptionLimit;
        if (truncated) description = description.substring(0, description.offsetByCodePoints(0, descriptionLimit)).stripTrailing();
        if (description.isBlank()) description = null;
        var applyUrl = urls.canonicalize(input.applyUrl());
        var sourceUrl = urls.canonicalize(input.sourceUrl());
        if (input.sourceType() == JobSourceType.MANUAL && description == null && applyUrl == null)
            throw new DomainValidationException("At least one of description or apply URL is required");
        validateSalary(input.salaryMinimum(), input.salaryMaximum());
        var currency = currency(input.salaryCurrency());
        var workplace = Objects.requireNonNullElse(input.workplaceType(), WorkplaceType.UNSPECIFIED);
        var employment = Objects.requireNonNullElse(input.employmentType(), EmploymentType.UNSPECIFIED);
        var interval = Objects.requireNonNullElse(input.salaryInterval(), SalaryInterval.UNSPECIFIED);
        var country = country(input.countryCode());
        var location = compact(input.location());
        var department = compact(input.department());
        var team = compact(input.team());
        var status = description == null && applyUrl == null ? JobPostingStatus.NEEDS_REVIEW : JobPostingStatus.READY_FOR_EVALUATION;
        if (input.expiresAt() != null && input.expiresAt().isBefore(Instant.now(clock))) status = JobPostingStatus.EXPIRED;
        var ingestionProvider = input.ingestionProvider() == null
                ? JobIngestionProvider.defaultFor(input.sourceType())
                : input.ingestionProvider();
        if (ingestionProvider == null) {
            throw new DomainValidationException("Ingestion provider is required for this source type");
        }
        var originPublisher = compactLimited(input.originPublisher(), 80, "Origin publisher");
        var discoveryQuery = compactLimited(input.discoveryQuery(), 500, "Discovery query");
        var extractionRecipeVersion = compactLimited(
                input.extractionRecipeVersion(), 80, "Extraction recipe version");
        var fingerprint = hash(identity(company, title, location, urls.hostAndPath(applyUrl)));
        var contentHash = hash(String.join("\u001f",
                company, title, nullSafe(location), nullSafe(country), workplace.name(), employment.name(),
                nullSafe(department), nullSafe(team), nullSafe(description), nullSafe(applyUrl), nullSafe(sourceUrl),
                decimal(input.salaryMinimum()), decimal(input.salaryMaximum()), nullSafe(currency), interval.name(),
                instant(input.publishedAt()), instant(input.sourceUpdatedAt()), instant(input.expiresAt())));
        return new NormalizedJob(input.sourceId(), input.sourceType(), externalId, company, title, location, country,
                workplace, employment, department, team, description, truncated, applyUrl, applyUrl, sourceUrl,
                input.salaryMinimum(), input.salaryMaximum(), currency, interval, input.publishedAt(),
                input.sourceUpdatedAt(), input.expiresAt(), fingerprint, contentHash, status, ingestionProvider,
                originPublisher, discoveryQuery, input.externalEventId(), extractionRecipeVersion);
    }

    private static String description(String value, boolean html) {
        if (value == null || value.isBlank()) return "";
        var text = value;
        if (html) {
            // Greenhouse documents that its content field contains HTML converted
            // to entities. Decode one provider encoding layer before parsing so
            // encoded tags do not survive as literal markup in stored text.
            var document = Jsoup.parse(Parser.unescapeEntities(value, false));
            document.select("script,style,iframe,object,embed,link,meta,noscript").remove();
            text = document.text();
        }
        text = Normalizer.normalize(text, Normalizer.Form.NFC)
                .replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", "")
                .replace("\u0000", "")
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll(" *\\r?\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n");
        return text.strip();
    }

    static String compact(String value) {
        if (value == null) return null;
        var result = Normalizer.normalize(value, Normalizer.Form.NFC)
                .replaceAll("[\\p{Cc}]", " ").replaceAll("\\s+", " ").trim();
        return result.isEmpty() ? null : result;
    }

    private static String compactLimited(String value, int maximumLength, String field) {
        var compacted = compact(value);
        if (compacted != null && compacted.length() > maximumLength) {
            throw new DomainValidationException(field + " exceeds " + maximumLength + " characters");
        }
        return compacted;
    }

    private static String identity(String company, String title, String location, String hostPath) {
        return company.toLowerCase(Locale.ROOT) + "\u001f" + title.toLowerCase(Locale.ROOT) + "\u001f"
                + nullSafe(location).toLowerCase(Locale.ROOT) + "\u001f" + hostPath;
    }
    private static String country(String value) { var v=compact(value); if(v==null)return null; v=v.toUpperCase(Locale.ROOT); return v.matches("[A-Z]{2}")?v:null; }
    private static String currency(String value) { var v=compact(value); if(v==null)return null; v=v.toUpperCase(Locale.ROOT); return v.matches("[A-Z]{3}")?v:null; }
    private static void validateSalary(BigDecimal min, BigDecimal max) { if(min!=null&&min.signum()<0||max!=null&&max.signum()<0||min!=null&&max!=null&&min.compareTo(max)>0)throw new DomainValidationException("Salary range is invalid"); }
    private static String decimal(BigDecimal v){return v==null?"":v.stripTrailingZeros().toPlainString();}
    private static String instant(Instant v){return v==null?"":v.toString();}
    private static String nullSafe(String v){return v==null?"":v;}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("SHA-256 is unavailable",e);}}
}
