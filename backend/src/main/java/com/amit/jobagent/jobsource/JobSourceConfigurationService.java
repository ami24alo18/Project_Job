package com.amit.jobagent.jobsource;

import com.amit.jobagent.audit.AuditEventType;
import com.amit.jobagent.audit.AuditService;
import com.amit.jobagent.common.error.ConflictException;
import com.amit.jobagent.common.error.DomainValidationException;
import com.amit.jobagent.common.error.ResourceNotFoundException;
import com.amit.jobagent.job.JobSourceType;
import com.amit.jobagent.jobsource.connector.SourceRegion;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobSourceConfigurationService{
    private final JobSourceConfigurationRepository repository;private final AuditService audit;
    public JobSourceConfigurationService(JobSourceConfigurationRepository repository,AuditService audit){this.repository=repository;this.audit=audit;}
    @Transactional public JobSourceConfigurationResponse create(JobSourceConfigurationRequest request){var normalized=validated(request);if(repository.existsBySourceTypeAndProviderIdentifierIgnoreCaseAndRegionAndArchivedAtIsNull(normalized.sourceType(),normalized.providerIdentifier(),normalized.region()))throw new ConflictException("An active configuration already exists for this provider");var saved=repository.saveAndFlush(new JobSourceConfiguration(normalized));audit.record(AuditEventType.JOB_SOURCE_CREATED,"JobSourceConfiguration",saved.getId(),"{\"sourceType\":\""+saved.sourceType()+"\"}");return map(saved);}
    @Transactional(readOnly=true)public List<JobSourceConfigurationResponse>list(){return repository.findAllByOrderByCreatedAtDescIdAsc().stream().map(JobSourceConfigurationService::map).toList();}
    @Transactional(readOnly=true)public JobSourceConfigurationResponse get(UUID id){return map(require(id));}
    @Transactional public JobSourceConfigurationResponse update(UUID id,JobSourceConfigurationRequest request){var source=require(id);checkVersion(source,request.recordVersion());var normalized=validated(request);if((source.sourceType()!=normalized.sourceType()||!source.providerIdentifier().equalsIgnoreCase(normalized.providerIdentifier())||source.region()!=normalized.region())&&repository.existsBySourceTypeAndProviderIdentifierIgnoreCaseAndRegionAndArchivedAtIsNull(normalized.sourceType(),normalized.providerIdentifier(),normalized.region()))throw new ConflictException("An active configuration already exists for this provider");source.apply(normalized,false);var saved=repository.saveAndFlush(source);audit.record(AuditEventType.JOB_SOURCE_UPDATED,"JobSourceConfiguration",id,"{}");return map(saved);}
    @Transactional public JobSourceConfigurationResponse enable(UUID id){var source=require(id);source.enable();var saved=repository.saveAndFlush(source);audit.record(AuditEventType.JOB_SOURCE_ENABLED,"JobSourceConfiguration",id,"{}");return map(saved);}
    @Transactional public JobSourceConfigurationResponse disable(UUID id){var source=require(id);source.disable();var saved=repository.saveAndFlush(source);audit.record(AuditEventType.JOB_SOURCE_DISABLED,"JobSourceConfiguration",id,"{}");return map(saved);}
    @Transactional public JobSourceConfigurationResponse archive(UUID id){var source=require(id);source.archive();var saved=repository.saveAndFlush(source);audit.record(AuditEventType.JOB_SOURCE_ARCHIVED,"JobSourceConfiguration",id,"{}");return map(saved);}
    @Transactional(readOnly=true)JobSourceConfiguration requireEntity(UUID id){return require(id);}
    @Transactional(readOnly=true)List<JobSourceConfiguration>enabledRemote(){return repository.findByEnabledTrueAndArchivedAtIsNullAndSourceTypeInOrderByCreatedAtAscIdAsc(List.of(JobSourceType.LEVER,JobSourceType.GREENHOUSE));}
    @Transactional(readOnly=true)JobSourceConfiguration emailSource(String name){return repository.findBySourceTypeAndProviderIdentifierIgnoreCaseAndArchivedAtIsNull(JobSourceType.EMAIL_WEBHOOK,name).filter(JobSourceConfiguration::enabled).orElseThrow(()->new ResourceNotFoundException("Enabled email-alert source was not found"));}
    private JobSourceConfiguration require(UUID id){return repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Job source was not found"));}
    private static void checkVersion(JobSourceConfiguration source,Long version){if(version==null||version!=source.getRecordVersion())throw new ConflictException("Job source was updated by another request");}
    private static JobSourceConfigurationRequest validated(JobSourceConfigurationRequest r){
        if(r.sourceType()==JobSourceType.MANUAL)throw new DomainValidationException("Manual jobs do not use a source configuration");
        var identifier=r.providerIdentifier().trim();
        var validPattern=r.sourceType()==JobSourceType.EMAIL_WEBHOOK
                ?"[A-Za-z0-9][A-Za-z0-9 ._-]{0,199}"
                :"[A-Za-z0-9][A-Za-z0-9._-]{0,199}";
        if(!identifier.matches(validPattern)||identifier.contains("://")||identifier.contains("/")||identifier.contains("\\\\"))throw new DomainValidationException("Provider identifier must be a site, board token, or logical source name, not a URL");
        var region=r.region();
        if(r.sourceType()==JobSourceType.LEVER){
            if(region==SourceRegion.DEFAULT)region=SourceRegion.GLOBAL;
            if(region!=SourceRegion.GLOBAL&&region!=SourceRegion.EU)throw new DomainValidationException("Lever supports GLOBAL or EU regions");
        }else if(region!=SourceRegion.DEFAULT)throw new DomainValidationException("This source type supports only the DEFAULT region");
        return new JobSourceConfigurationRequest(r.displayName(),r.sourceType(),identifier,region,r.enabled()==null?Boolean.TRUE:r.enabled(),r.pageSize(),r.maximumPagesPerRun(),r.missingRunThreshold(),r.recordVersion());
    }
    static JobSourceConfigurationResponse map(JobSourceConfiguration s){return new JobSourceConfigurationResponse(s.getId(),s.displayName(),s.sourceType(),s.providerIdentifier(),s.region(),s.enabled(),s.pageSize(),s.maximumPagesPerRun(),s.missingRunThreshold(),s.lastSuccessfulSyncAt(),s.lastAttemptedSyncAt(),s.consecutiveFailureCount(),s.getRecordVersion(),s.getCreatedAt(),s.getUpdatedAt(),s.archivedAt());}
}
