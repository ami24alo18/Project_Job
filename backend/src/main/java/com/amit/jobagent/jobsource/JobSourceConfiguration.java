package com.amit.jobagent.jobsource;

import com.amit.jobagent.common.error.DomainValidationException;
import com.amit.jobagent.common.persistence.MutableEntity;
import com.amit.jobagent.job.JobSourceType;
import com.amit.jobagent.jobsource.connector.SourceRegion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "job_source_configuration")
class JobSourceConfiguration extends MutableEntity {
    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private JobSourceType sourceType;
    @Column(name = "provider_identifier", nullable = false, length = 200)
    private String providerIdentifier;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SourceRegion region;
    @Enumerated(EnumType.STRING)
    @Column(name = "connector_type", length = 40)
    private JobSourceConnectorType connectorType;
    @Column(name = "career_site_url", length = 2000)
    private String careerSiteUrl;
    @Column(name = "canonical_host", length = 253)
    private String canonicalHost;
    @Enumerated(EnumType.STRING)
    @Column(name = "support_status", nullable = false, length = 40)
    private JobSourceSupportStatus supportStatus;
    @Column(name = "support_message", length = 500)
    private String supportMessage;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "webhook_token_hash", length = 64, columnDefinition = "char(64)")
    private String webhookTokenHash;
    @Column(name = "detection_version", length = 80)
    private String detectionVersion;
    @Column(name = "extraction_recipe_version", length = 80)
    private String extractionRecipeVersion;
    @Column(name = "last_connection_test_at")
    private Instant lastConnectionTestAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "last_connection_test_status", length = 40)
    private JobSourceConnectionTestStatus lastConnectionTestStatus;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "page_size", nullable = false)
    private int pageSize;
    @Column(name = "maximum_pages_per_run", nullable = false)
    private int maximumPagesPerRun;
    @Column(name = "missing_run_threshold", nullable = false)
    private int missingRunThreshold;
    @Column(name = "last_successful_sync_at")
    private Instant lastSuccessfulSyncAt;
    @Column(name = "last_attempted_sync_at")
    private Instant lastAttemptedSyncAt;
    @Column(name = "consecutive_failure_count", nullable = false)
    private int consecutiveFailureCount;
    @Column(name = "archived_at")
    private Instant archivedAt;

    protected JobSourceConfiguration() {}

    JobSourceConfiguration(JobSourceConfigurationRequest request) {
        supportStatus = JobSourceSupportStatus.SUPPORTED;
        apply(request, true);
        connectorType = JobSourceConnectorType.defaultFor(sourceType);
    }

    static JobSourceConfiguration external(
            String displayName,
            String providerIdentifier,
            JobSourceConnectorType connectorType,
            boolean enabled,
            String webhookTokenHash) {
        var source = new JobSourceConfiguration();
        source.displayName = displayName;
        source.sourceType = JobSourceType.EXTERNAL_API;
        source.providerIdentifier = providerIdentifier;
        source.region = SourceRegion.DEFAULT;
        source.connectorType = connectorType;
        source.supportStatus = JobSourceSupportStatus.SUPPORTED;
        source.enabled = enabled;
        source.pageSize = 100;
        source.maximumPagesPerRun = 1;
        source.missingRunThreshold = 2;
        source.webhookTokenHash = webhookTokenHash;
        return source;
    }

    static JobSourceConfiguration careerSite(
            CareerSiteJobSourceRequest request,
            com.amit.jobagent.jobsource.discovery.CareerSiteDiscoveryResponse detection) {
        var source = new JobSourceConfiguration();
        source.displayName = request.companyName().trim();
        source.sourceType = JobSourceType.CAREER_SITE;
        source.providerIdentifier = detection.providerIdentifier();
        source.region = SourceRegion.DEFAULT;
        source.connectorType = detection.connectorType();
        source.careerSiteUrl = detection.canonicalUrl();
        source.canonicalHost = detection.canonicalHost();
        source.supportStatus = detection.supportStatus();
        source.supportMessage = detection.supportMessage();
        source.detectionVersion = detection.detectionVersion();
        source.enabled = detection.supportStatus() == JobSourceSupportStatus.SUPPORTED
                && (request.enabled() == null || request.enabled());
        source.pageSize = request.pageSize();
        source.maximumPagesPerRun = request.maximumPagesPerRun();
        source.missingRunThreshold = request.missingRunThreshold();
        return source;
    }

    void apply(JobSourceConfigurationRequest request, boolean initial) {
        if (archivedAt != null) throw new DomainValidationException("Archived job sources cannot be edited");
        var previousSourceType = sourceType;
        displayName = request.displayName().trim();
        sourceType = request.sourceType();
        providerIdentifier = request.providerIdentifier().trim();
        region = request.region();
        enabled = Boolean.TRUE.equals(request.enabled());
        pageSize = request.pageSize();
        maximumPagesPerRun = request.maximumPagesPerRun();
        missingRunThreshold = request.missingRunThreshold();
        if (initial || previousSourceType != sourceType) connectorType = JobSourceConnectorType.defaultFor(sourceType);
        if (!initial) touch();
    }

    JobSourceCategory sourceCategory() {
        if (sourceType == JobSourceType.EMAIL_WEBHOOK) return JobSourceCategory.EMAIL_WEBHOOK;
        if (sourceType == JobSourceType.EXTERNAL_API || connectorType == JobSourceConnectorType.CUSTOM_RECIPE) {
            return JobSourceCategory.PUSH_WEBHOOK;
        }
        return JobSourceCategory.PULL_FEED;
    }

    void enable() { if (archivedAt != null) throw new DomainValidationException("Archived job sources cannot be enabled"); if(supportStatus!=JobSourceSupportStatus.SUPPORTED)throw new DomainValidationException("This job source is not supported for activation"); enabled = true; touch(); }
    void disable() { enabled = false; touch(); }
    void archive() { enabled = false; archivedAt = Instant.now(); touch(); }
    void attempted(Instant at) { lastAttemptedSyncAt = at; touch(); }
    void succeeded(Instant at) { lastSuccessfulSyncAt = at; consecutiveFailureCount = 0; touch(); }
    void failed() { consecutiveFailureCount++; touch(); }
    void rotateWebhookToken(String tokenHash) { webhookTokenHash = tokenHash; touch(); }
    void associateRecipe(String version, String tokenHash, boolean activate) {
        if (archivedAt != null || sourceType != JobSourceType.CAREER_SITE
                || connectorType != JobSourceConnectorType.CUSTOM_RECIPE
                || supportStatus != JobSourceSupportStatus.NEEDS_EXTRACTION_RECIPE) {
            throw new DomainValidationException("This source cannot be associated with an extraction recipe");
        }
        extractionRecipeVersion = version;
        webhookTokenHash = tokenHash;
        supportStatus = JobSourceSupportStatus.SUPPORTED;
        supportMessage = "A reviewed isolated extraction recipe is associated";
        enabled = activate;
        touch();
    }
    void connectionTested(Instant at, JobSourceConnectionTestStatus status) { lastConnectionTestAt = at; lastConnectionTestStatus = status; touch(); }
    String displayName() { return displayName; }
    JobSourceType sourceType() { return sourceType; }
    String providerIdentifier() { return providerIdentifier; }
    SourceRegion region() { return region; }
    JobSourceConnectorType connectorType() { return connectorType; }
    String careerSiteUrl() { return careerSiteUrl; }
    String canonicalHost() { return canonicalHost; }
    JobSourceSupportStatus supportStatus() { return supportStatus; }
    String supportMessage() { return supportMessage; }
    boolean webhookConfigured() { return webhookTokenHash != null; }
    String webhookTokenHash() { return webhookTokenHash; }
    String detectionVersion() { return detectionVersion; }
    String extractionRecipeVersion() { return extractionRecipeVersion; }
    Instant lastConnectionTestAt() { return lastConnectionTestAt; }
    JobSourceConnectionTestStatus lastConnectionTestStatus() { return lastConnectionTestStatus; }
    boolean enabled() { return enabled; }
    int pageSize() { return pageSize; }
    int maximumPagesPerRun() { return maximumPagesPerRun; }
    int missingRunThreshold() { return missingRunThreshold; }
    Instant lastSuccessfulSyncAt() { return lastSuccessfulSyncAt; }
    Instant lastAttemptedSyncAt() { return lastAttemptedSyncAt; }
    int consecutiveFailureCount() { return consecutiveFailureCount; }
    Instant archivedAt() { return archivedAt; }
}
