package com.amit.jobagent.job;

import com.amit.jobagent.audit.AuditEventType;
import com.amit.jobagent.audit.AuditService;
import com.amit.jobagent.common.api.PagedResponse;
import com.amit.jobagent.common.error.ConflictException;
import com.amit.jobagent.common.error.DomainValidationException;
import com.amit.jobagent.common.error.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobPostingService {
    private static final EnumSet<JobPostingStatus> ACTIVE_DEDUP_STATUSES=EnumSet.of(JobPostingStatus.READY_FOR_EVALUATION,JobPostingStatus.NEEDS_REVIEW);
    private final JobPostingRepository repository;private final JobNormalizationService normalization;private final AuditService audit;private final Clock clock;
    public JobPostingService(JobPostingRepository repository,JobNormalizationService normalization,AuditService audit,Clock clock){this.repository=repository;this.normalization=normalization;this.audit=audit;this.clock=clock;}

    @Transactional
    public JobPostingResponse createManual(ManualJobRequest r,String idempotencyKey){
        var key=cleanKey(idempotencyKey);if(key!=null){var prior=repository.findByManualIdempotencyKey(key);if(prior.isPresent())return map(prior.get());}
        var candidate=manualCandidate(r,"manual-"+UUID.randomUUID());return map(create(normalization.normalize(candidate),null,key).job());
    }
    @Transactional
    public JobIngestionOutcome ingest(JobCandidate candidate,UUID runId){
        var value=normalization.normalize(candidate);
        if(value.sourceId()==null||value.sourceType()==JobSourceType.MANUAL)throw new DomainValidationException("Synchronized jobs require a configured source");
        var existing=repository.findBySourceIdAndExternalId(value.sourceId(),value.externalId());
        if(existing.isPresent()){
            var job=existing.get();var priorStatus=job.status();boolean changed=job.observeProvider(value,runId,Instant.now(clock));repository.saveAndFlush(job);
            if(changed)audit.record(AuditEventType.JOB_UPDATED,"JobPosting",job.getId(),"{\"sourceUpdateAvailable\":"+job.sourceUpdateAvailable()+"}");
            if(priorStatus!=JobPostingStatus.EXPIRED&&job.status()==JobPostingStatus.EXPIRED)audit.record(AuditEventType.JOB_MARKED_EXPIRED,"JobPosting",job.getId(),"{}");
            return new JobIngestionOutcome(job.getId(),changed?JobIngestionAction.UPDATED:JobIngestionAction.UNCHANGED);
        }
        var created=create(value,runId,null);return new JobIngestionOutcome(created.job().getId(),created.duplicate()?JobIngestionAction.DUPLICATE:JobIngestionAction.CREATED);
    }
    private Created create(NormalizedJob value,UUID runId,String key){
        var job=new JobPosting(value,runId,key,Instant.now(clock));var duplicate=findDuplicate(value);
        duplicate.ifPresent(target->job.markDuplicate(target.getId()));var saved=repository.saveAndFlush(job);
        audit.record(AuditEventType.JOB_CREATED,"JobPosting",saved.getId(),"{\"sourceType\":\""+saved.sourceType()+"\"}");
        if(duplicate.isPresent())audit.record(AuditEventType.JOB_MARKED_DUPLICATE,"JobPosting",saved.getId(),"{\"duplicateOfJobId\":\""+duplicate.get().getId()+"\"}");
        return new Created(saved,duplicate.isPresent());
    }
    private java.util.Optional<JobPosting> findDuplicate(NormalizedJob value){
        if(value.canonicalApplyUrl()!=null){var byUrl=repository.findFirstByCanonicalApplyUrlAndStatusInOrderByFirstSeenAtAscIdAsc(value.canonicalApplyUrl(),ACTIVE_DEDUP_STATUSES);if(byUrl.isPresent())return byUrl;}
        return repository.findFirstByFingerprintAndStatusInOrderByFirstSeenAtAscIdAsc(value.fingerprint(),ACTIVE_DEDUP_STATUSES);
    }
    @Transactional
    public MissingJobResult markMissingAfterSuccessfulRun(UUID sourceId,UUID runId,int threshold){int removed=0;for(var job:repository.findBySourceId(sourceId)){if(runId.equals(job.lastSeenRunId()))continue;if(job.incrementMissing(threshold)){removed++;audit.record(AuditEventType.JOB_MARKED_SOURCE_REMOVED,"JobPosting",job.getId(),"{}");}}repository.flush();return new MissingJobResult(removed);}
    @Transactional(readOnly=true)public JobPostingResponse get(UUID id){return map(require(id));}
    @Transactional(readOnly=true)public PagedResponse<JobPostingResponse> list(UUID sourceId,JobSourceType sourceType,JobPostingStatus status,String company,String title,String location,WorkplaceType workplaceType,EmploymentType employmentType,Instant publishedFrom,Instant publishedTo,Instant firstSeenFrom,Instant firstSeenTo,boolean includeDuplicates,boolean includeArchived,int page,int requestedSize,String sort){
        Specification<JobPosting> spec=(root,q,cb)->{var predicates=new ArrayList<Predicate>();if(!includeDuplicates)predicates.add(cb.notEqual(root.get("status"),JobPostingStatus.DUPLICATE));if(!includeArchived)predicates.add(cb.notEqual(root.get("status"),JobPostingStatus.ARCHIVED));return cb.and(predicates.toArray(Predicate[]::new));};
        if(sourceId!=null)spec=spec.and((r,q,c)->c.equal(r.get("sourceId"),sourceId));if(sourceType!=null)spec=spec.and((r,q,c)->c.equal(r.get("sourceType"),sourceType));if(status!=null)spec=spec.and((r,q,c)->c.equal(r.get("status"),status));
        spec=like(spec,"company",company);spec=like(spec,"title",title);spec=like(spec,"location",location);if(workplaceType!=null)spec=spec.and((r,q,c)->c.equal(r.get("workplaceType"),workplaceType));if(employmentType!=null)spec=spec.and((r,q,c)->c.equal(r.get("employmentType"),employmentType));
        if(publishedFrom!=null)spec=spec.and((r,q,c)->c.greaterThanOrEqualTo(r.get("publishedAt"),publishedFrom));if(publishedTo!=null)spec=spec.and((r,q,c)->c.lessThanOrEqualTo(r.get("publishedAt"),publishedTo));if(firstSeenFrom!=null)spec=spec.and((r,q,c)->c.greaterThanOrEqualTo(r.get("firstSeenAt"),firstSeenFrom));if(firstSeenTo!=null)spec=spec.and((r,q,c)->c.lessThanOrEqualTo(r.get("firstSeenAt"),firstSeenTo));
        var result=repository.findAll(spec,PageRequest.of(Math.max(page,0),Math.min(Math.max(requestedSize,1),100),sort(sort)));return new PagedResponse<>(result.map(JobPostingService::map).getContent(),result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages());
    }
    private static Specification<JobPosting>like(Specification<JobPosting>spec,String field,String value){if(value==null||value.isBlank())return spec;var term="%"+value.trim().toLowerCase(Locale.ROOT)+"%";return spec.and((r,q,c)->c.like(c.lower(r.get(field)),term));}
    private static Sort sort(String requested){var value=requested==null?"firstSeenAt,desc":requested;var parts=value.split(",",2);var field=switch(parts[0]){case"publishedAt","company","title","firstSeenAt"->parts[0];default->"firstSeenAt";};var direction=parts.length>1&&parts[1].equalsIgnoreCase("asc")?Sort.Direction.ASC:Sort.Direction.DESC;return Sort.by(direction,field).and(Sort.by(Sort.Direction.ASC,"id"));}
    @Transactional public JobPostingResponse update(UUID id,JobUpdateRequest r){var job=require(id);checkVersion(job,r.recordVersion());var value=normalization.normalize(updateCandidate(job,r));job.edit(value);var saved=repository.saveAndFlush(job);audit.record(job.sourceType()==JobSourceType.MANUAL?AuditEventType.JOB_UPDATED:AuditEventType.JOB_MANUALLY_EDITED,"JobPosting",id,"{}");return map(saved);}
    @Transactional public JobPostingResponse archive(UUID id){var job=require(id);job.archive();var saved=repository.saveAndFlush(job);audit.record(AuditEventType.JOB_ARCHIVED,"JobPosting",id,"{}");return map(saved);}
    @Transactional public JobPostingResponse restore(UUID id){var job=require(id);job.restore(Instant.now(clock));var saved=repository.saveAndFlush(job);audit.record(AuditEventType.JOB_RESTORED,"JobPosting",id,"{}");return map(saved);}
    @Transactional public JobPostingResponse markExpired(UUID id){var job=require(id);job.markExpired();var saved=repository.saveAndFlush(job);audit.record(AuditEventType.JOB_MARKED_EXPIRED,"JobPosting",id,"{}");return map(saved);}
    @Transactional(readOnly=true)public java.util.List<JobPostingResponse>duplicates(UUID id){require(id);return repository.findByDuplicateOfJobIdOrderByFirstSeenAtAscIdAsc(id).stream().map(JobPostingService::map).toList();}
    @Transactional(readOnly=true)public JobSummaryResponse summary(){return new JobSummaryResponse(repository.count(),repository.countByStatus(JobPostingStatus.READY_FOR_EVALUATION),repository.countByStatus(JobPostingStatus.NEEDS_REVIEW),repository.countByStatus(JobPostingStatus.DUPLICATE),repository.countByStatus(JobPostingStatus.EXPIRED),repository.countByStatus(JobPostingStatus.SOURCE_REMOVED),repository.countByStatus(JobPostingStatus.ARCHIVED));}
    private JobPosting require(UUID id){return repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Job was not found"));}
    private static void checkVersion(JobPosting job,Long version){if(version==null||version!=job.getRecordVersion())throw new ConflictException("Job was updated by another request");}
    private static String cleanKey(String key){if(key==null||key.isBlank())return null;var v=key.trim();if(v.length()>200||!v.matches("[A-Za-z0-9._:-]+"))throw new DomainValidationException("Idempotency-Key is invalid");return v;}
    private static JobCandidate manualCandidate(ManualJobRequest r,String externalId){return new JobCandidate(null,JobSourceType.MANUAL,externalId,r.company(),r.title(),r.location(),r.countryCode(),r.workplaceType(),r.employmentType(),r.department(),r.team(),r.description(),true,r.applyUrl(),r.sourceUrl(),r.salaryMinimum(),r.salaryMaximum(),r.salaryCurrency(),r.salaryInterval(),r.publishedAt(),null,r.expiresAt());}
    private static JobCandidate updateCandidate(JobPosting j,JobUpdateRequest r){return new JobCandidate(j.sourceId(),j.sourceType(),j.externalId(),r.company(),r.title(),r.location(),r.countryCode(),r.workplaceType(),r.employmentType(),r.department(),r.team(),r.description(),true,r.applyUrl(),r.sourceUrl(),r.salaryMinimum(),r.salaryMaximum(),r.salaryCurrency(),r.salaryInterval(),r.publishedAt(),j.sourceUpdatedAt(),r.expiresAt(),j.ingestionProvider(),j.originPublisher(),j.discoveryQuery(),j.externalEventId(),j.extractionRecipeVersion());}
    static JobPostingResponse map(JobPosting j){return new JobPostingResponse(j.getId(),j.sourceId(),j.sourceType(),j.externalId(),j.duplicateOfJobId(),j.company(),j.title(),j.location(),j.countryCode(),j.workplaceType(),j.employmentType(),j.department(),j.team(),j.descriptionPlainText(),j.descriptionTruncated(),j.applyUrl(),j.sourceUrl(),j.salaryMinimum(),j.salaryMaximum(),j.salaryCurrency(),j.salaryInterval(),j.publishedAt(),j.sourceUpdatedAt(),j.expiresAt(),j.firstSeenAt(),j.lastSeenAt(),j.missingSuccessfulRunCount(),j.fingerprint(),j.contentHash(),j.status(),j.manuallyEdited(),j.sourceUpdateAvailable(),j.ingestionProvider(),j.originPublisher(),j.discoveryQuery(),j.externalEventId(),j.extractionRecipeVersion(),j.getRecordVersion(),j.getCreatedAt(),j.getUpdatedAt());}
    private record Created(JobPosting job,boolean duplicate){}
}
