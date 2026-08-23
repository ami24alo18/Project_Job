package com.amit.jobagent.job;

import com.amit.jobagent.common.error.DomainValidationException;
import com.amit.jobagent.common.persistence.MutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="job_posting")
class JobPosting extends MutableEntity {
    @Column(name="source_id") private UUID sourceId;
    @Enumerated(EnumType.STRING) @Column(name="source_type",nullable=false,length=30) private JobSourceType sourceType;
    @Column(name="external_id",nullable=false,length=300) private String externalId;
    @Column(name="duplicate_of_job_id") private UUID duplicateOfJobId;
    @Column(nullable=false,length=200) private String company;
    @Column(nullable=false,length=300) private String title;
    @Column(length=300) private String location;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="country_code",length=2,columnDefinition="char(2)") private String countryCode;
    @Enumerated(EnumType.STRING) @Column(name="workplace_type",nullable=false,length=30) private WorkplaceType workplaceType;
    @Enumerated(EnumType.STRING) @Column(name="employment_type",nullable=false,length=30) private EmploymentType employmentType;
    @Column(length=200) private String department;
    @Column(length=200) private String team;
    @Column(name="description_plain_text",columnDefinition="text") private String descriptionPlainText;
    @Column(name="description_truncated",nullable=false) private boolean descriptionTruncated;
    @Column(name="apply_url",length=2000) private String applyUrl;
    @Column(name="canonical_apply_url",length=2000) private String canonicalApplyUrl;
    @Column(name="source_url",length=2000) private String sourceUrl;
    @Column(name="salary_minimum",precision=19,scale=4) private BigDecimal salaryMinimum;
    @Column(name="salary_maximum",precision=19,scale=4) private BigDecimal salaryMaximum;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="salary_currency",length=3,columnDefinition="char(3)") private String salaryCurrency;
    @Enumerated(EnumType.STRING) @Column(name="salary_interval",nullable=false,length=30) private SalaryInterval salaryInterval;
    @Column(name="published_at") private Instant publishedAt;
    @Column(name="source_updated_at") private Instant sourceUpdatedAt;
    @Column(name="expires_at") private Instant expiresAt;
    @Column(name="first_seen_at",nullable=false,updatable=false) private Instant firstSeenAt;
    @Column(name="last_seen_at",nullable=false) private Instant lastSeenAt;
    @Column(name="missing_successful_run_count",nullable=false) private int missingSuccessfulRunCount;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable=false,length=64,columnDefinition="char(64)") private String fingerprint;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="content_hash",nullable=false,length=64,columnDefinition="char(64)") private String contentHash;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="source_content_hash",nullable=false,length=64,columnDefinition="char(64)") private String sourceContentHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private JobPostingStatus status;
    @Enumerated(EnumType.STRING) @Column(name="status_before_archive",length=30) private JobPostingStatus statusBeforeArchive;
    @Column(name="manually_edited",nullable=false) private boolean manuallyEdited;
    @Column(name="source_update_available",nullable=false) private boolean sourceUpdateAvailable;
    @Column(name="last_seen_run_id") private UUID lastSeenRunId;
    @Column(name="manual_idempotency_key",length=200) private String manualIdempotencyKey;

    protected JobPosting() {}
    JobPosting(NormalizedJob value, UUID runId, String idempotencyKey, Instant now) {
        this.id=UUID.randomUUID(); sourceId=value.sourceId(); sourceType=value.sourceType(); externalId=value.externalId();
        firstSeenAt=now; lastSeenAt=now; lastSeenRunId=runId; manualIdempotencyKey=idempotencyKey;
        apply(value); status=value.initialStatus(); sourceContentHash=value.contentHash();
    }
    private void apply(NormalizedJob v) {
        company=v.company();title=v.title();location=v.location();countryCode=v.countryCode();workplaceType=v.workplaceType();
        employmentType=v.employmentType();department=v.department();team=v.team();descriptionPlainText=v.descriptionPlainText();
        descriptionTruncated=v.descriptionTruncated();applyUrl=v.applyUrl();canonicalApplyUrl=v.canonicalApplyUrl();
        sourceUrl=v.sourceUrl();salaryMinimum=v.salaryMinimum();salaryMaximum=v.salaryMaximum();salaryCurrency=v.salaryCurrency();
        salaryInterval=v.salaryInterval();publishedAt=v.publishedAt();sourceUpdatedAt=v.sourceUpdatedAt();expiresAt=v.expiresAt();
        fingerprint=v.fingerprint();contentHash=v.contentHash();
    }
    boolean observeProvider(NormalizedJob value, UUID runId, Instant now) {
        lastSeenAt=now;lastSeenRunId=runId;missingSuccessfulRunCount=0;
        var previousExpiry=expiresAt;
        boolean changed=!sourceContentHash.equals(value.contentHash());
        if(changed){
            sourceContentHash=value.contentHash();
            if(manuallyEdited)sourceUpdateAvailable=true;
            else{apply(value);sourceUpdateAvailable=false;}
        }
        if(status==JobPostingStatus.SOURCE_REMOVED){
            status=value.initialStatus();
        }else if(status!=JobPostingStatus.ARCHIVED&&status!=JobPostingStatus.DUPLICATE){
            boolean trustedExpiryPast=value.initialStatus()==JobPostingStatus.EXPIRED;
            boolean trustedExpiryChanged=!Objects.equals(previousExpiry,value.expiresAt());
            if(trustedExpiryPast||(status==JobPostingStatus.EXPIRED&&trustedExpiryChanged)){
                status=value.initialStatus();
            }
        }
        touch();return changed;
    }
    void edit(NormalizedJob value) {
        if(status==JobPostingStatus.ARCHIVED)throw new DomainValidationException("Archived jobs must be restored before editing");
        var retained=status;apply(value);manuallyEdited=sourceType!=JobSourceType.MANUAL;sourceUpdateAvailable=false;
        if(retained==JobPostingStatus.DUPLICATE)status=retained;else status=value.initialStatus();touch();
    }
    void markDuplicate(UUID targetId){duplicateOfJobId=targetId;status=JobPostingStatus.DUPLICATE;touch();}
    void archive(){if(status!=JobPostingStatus.ARCHIVED){statusBeforeArchive=status;status=JobPostingStatus.ARCHIVED;touch();}}
    void restore(Instant now){if(status!=JobPostingStatus.ARCHIVED)throw new DomainValidationException("Only archived jobs can be restored");status=statusBeforeArchive==null?JobPostingStatus.READY_FOR_EVALUATION:statusBeforeArchive;if(expiresAt!=null&&expiresAt.isBefore(now))status=JobPostingStatus.EXPIRED;statusBeforeArchive=null;touch();}
    void markExpired(){if(status==JobPostingStatus.ARCHIVED)throw new DomainValidationException("Archived jobs cannot be marked expired");status=JobPostingStatus.EXPIRED;touch();}
    boolean incrementMissing(int threshold){if(status!=JobPostingStatus.READY_FOR_EVALUATION&&status!=JobPostingStatus.NEEDS_REVIEW)return false;missingSuccessfulRunCount++;if(missingSuccessfulRunCount>=threshold){status=JobPostingStatus.SOURCE_REMOVED;touch();return true;}touch();return false;}
    UUID sourceId(){return sourceId;} JobSourceType sourceType(){return sourceType;} String externalId(){return externalId;}
    UUID duplicateOfJobId(){return duplicateOfJobId;} String company(){return company;} String title(){return title;}
    String location(){return location;} String countryCode(){return countryCode;} WorkplaceType workplaceType(){return workplaceType;}
    EmploymentType employmentType(){return employmentType;} String department(){return department;} String team(){return team;}
    String descriptionPlainText(){return descriptionPlainText;} boolean descriptionTruncated(){return descriptionTruncated;}
    String applyUrl(){return applyUrl;} String canonicalApplyUrl(){return canonicalApplyUrl;} String sourceUrl(){return sourceUrl;}
    BigDecimal salaryMinimum(){return salaryMinimum;} BigDecimal salaryMaximum(){return salaryMaximum;} String salaryCurrency(){return salaryCurrency;}
    SalaryInterval salaryInterval(){return salaryInterval;} Instant publishedAt(){return publishedAt;} Instant sourceUpdatedAt(){return sourceUpdatedAt;}
    Instant expiresAt(){return expiresAt;} Instant firstSeenAt(){return firstSeenAt;} Instant lastSeenAt(){return lastSeenAt;}
    int missingSuccessfulRunCount(){return missingSuccessfulRunCount;} String fingerprint(){return fingerprint;} String contentHash(){return contentHash;}
    String sourceContentHash(){return sourceContentHash;} JobPostingStatus status(){return status;} boolean manuallyEdited(){return manuallyEdited;}
    boolean sourceUpdateAvailable(){return sourceUpdateAvailable;} UUID lastSeenRunId(){return lastSeenRunId;}
}
