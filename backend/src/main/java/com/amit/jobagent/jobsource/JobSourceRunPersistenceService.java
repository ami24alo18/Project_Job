package com.amit.jobagent.jobsource;

import com.amit.jobagent.audit.AuditEventType;
import com.amit.jobagent.audit.AuditService;
import com.amit.jobagent.common.api.PagedResponse;
import com.amit.jobagent.common.config.JobIngestionProperties;
import com.amit.jobagent.common.error.ConflictException;
import com.amit.jobagent.common.error.DomainValidationException;
import com.amit.jobagent.common.error.ResourceNotFoundException;
import com.amit.jobagent.job.JobSourceType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class JobSourceRunPersistenceService{
    private static final EnumSet<JobSourceRunStatus>ACTIVE=EnumSet.of(JobSourceRunStatus.QUEUED,JobSourceRunStatus.RUNNING);
    private final JobSourceRunRepository runs;private final JobSourceRunErrorRepository errors;private final JobSourceConfigurationRepository sources;private final AuditService audit;private final Clock clock;private final Duration staleAfter;
    JobSourceRunPersistenceService(JobSourceRunRepository runs,JobSourceRunErrorRepository errors,JobSourceConfigurationRepository sources,AuditService audit,Clock clock,JobIngestionProperties properties){this.runs=runs;this.errors=errors;this.sources=sources;this.audit=audit;this.clock=clock;staleAfter=Duration.ofMinutes(properties.staleRunAfterMinutes());}
    @Transactional JobSourceRunResponse queue(UUID sourceId,JobSourceTriggerType trigger){reconcileStaleInternal(sourceId);var source=sources.findForUpdateById(sourceId).orElseThrow(()->new ResourceNotFoundException("Job source was not found"));if(source.archivedAt()!=null)throw new DomainValidationException("Archived job sources cannot be synchronized");if(!source.enabled())throw new DomainValidationException("Disabled job sources cannot be synchronized");if(!remotelySynchronized(source))throw new DomainValidationException("This source type is not remotely synchronized");if(runs.existsBySourceIdAndStatusIn(sourceId,ACTIVE))throw new ConflictException("A synchronization run is already active for this source");var run=runs.saveAndFlush(new JobSourceRun(sourceId,trigger,coverage(source),null,null,Instant.now(clock)));return map(run,List.of());}
    @Transactional JobSourceRunContext begin(UUID runId){var run=require(runId);var source=sources.findForUpdateById(run.sourceId()).orElseThrow(()->new ResourceNotFoundException("Job source was not found"));if(source.archivedAt()!=null||!source.enabled())throw new DomainValidationException("Job source is no longer enabled");var now=Instant.now(clock);if(!run.start(now))throw new ConflictException("The synchronization run is no longer queued");source.attempted(now);runs.saveAndFlush(run);sources.saveAndFlush(source);audit.record(AuditEventType.JOB_SOURCE_SYNC_STARTED,"JobSourceRun",runId,"{\"sourceId\":\""+source.getId()+"\"}");return new JobSourceRunContext(runId,source.getId(),source.displayName(),source.sourceType(),source.connectorType(),source.providerIdentifier(),source.region(),source.careerSiteUrl(),source.canonicalHost(),source.pageSize(),source.maximumPagesPerRun(),source.missingRunThreshold(),run.coverage());}
    private static boolean remotelySynchronized(JobSourceConfiguration source){return source.connectorType()==JobSourceConnectorType.LEVER||source.connectorType()==JobSourceConnectorType.GREENHOUSE||source.connectorType()==JobSourceConnectorType.SMARTRECRUITERS||source.connectorType()==JobSourceConnectorType.GENERIC_JSON_LD;}
    private static JobSourceRunCoverage coverage(JobSourceConfiguration source){return source.connectorType()==JobSourceConnectorType.GENERIC_JSON_LD?JobSourceRunCoverage.FILTERED_QUERY:JobSourceRunCoverage.COMPLETE_INVENTORY;}
    @Transactional void complete(UUID runId,JobSourceRunStatus status,String checkpoint,RunMetrics metrics,List<RunProblem>problems){var run=require(runId);var source=sources.findForUpdateById(run.sourceId()).orElseThrow(()->new ResourceNotFoundException("Job source was not found"));var now=Instant.now(clock);String code=problems.isEmpty()?null:status==JobSourceRunStatus.PARTIAL_SUCCESS?"PARTIAL_RECORD_FAILURE":"RUN_FAILURE";String message=problems.isEmpty()?null:problems.size()+" job record(s) could not be processed";if(!run.complete(status,checkpoint,metrics,now,code,message))throw new ConflictException("The synchronization run is no longer running");for(var problem:problems)errors.save(new JobSourceRunError(runId,problem.externalId(),problem.code(),problem.message(),now));if(status==JobSourceRunStatus.SUCCEEDED)source.succeeded(now);else source.failed();runs.saveAndFlush(run);sources.saveAndFlush(source);audit.record(AuditEventType.JOB_SOURCE_SYNC_COMPLETED,"JobSourceRun",runId,"{\"status\":\""+status+"\",\"created\":"+metrics.created()+",\"updated\":"+metrics.updated()+"}");}
    @Transactional void fail(UUID runId,String code,String message){var run=require(runId);if(run.status()==JobSourceRunStatus.SUCCEEDED||run.status()==JobSourceRunStatus.PARTIAL_SUCCESS||run.status()==JobSourceRunStatus.FAILED)return;var source=sources.findForUpdateById(run.sourceId()).orElse(null);run.fail(code,message,Instant.now(clock));runs.saveAndFlush(run);if(source!=null){source.failed();sources.saveAndFlush(source);}audit.record(AuditEventType.JOB_SOURCE_SYNC_FAILED,"JobSourceRun",runId,"{\"errorCode\":\""+safe(code)+"\"}");}
    @Transactional(readOnly=true) String resumableCheckpoint(UUID sourceId,UUID currentRunId){return runs.findFirstBySourceIdAndIdNotOrderByCreatedAtDescIdDesc(sourceId,currentRunId).filter(run->run.status()==JobSourceRunStatus.PARTIAL_SUCCESS).map(JobSourceRun::checkpoint).orElse(null);}
    @Transactional(readOnly=true) Optional<JobSourceRunResponse> active(UUID sourceId){return runs.findFirstBySourceIdAndStatusInOrderByCreatedAtDescIdDesc(sourceId,ACTIVE).map(run->map(run,List.of()));}
    @Transactional void reconcileStale(){var now=Instant.now(clock);for(var run:runs.findByStatusIn(ACTIVE))if(stale(run,now))recoverStale(run,now);runs.flush();sources.flush();}
    private void reconcileStaleInternal(UUID sourceId){var now=Instant.now(clock);for(var run:runs.findByStatusIn(ACTIVE))if(run.sourceId().equals(sourceId)&&stale(run,now))recoverStale(run,now);runs.flush();sources.flush();}
    private void recoverStale(JobSourceRun run,Instant now){run.fail("STALE_RUN_RECOVERED","The run was recovered after an interrupted process",now);runs.save(run);sources.findForUpdateById(run.sourceId()).ifPresent(source->{source.failed();sources.save(source);});audit.record(AuditEventType.JOB_SOURCE_SYNC_FAILED,"JobSourceRun",run.id(),"{\"errorCode\":\"STALE_RUN_RECOVERED\"}");}
    private boolean stale(JobSourceRun run,Instant now){var since=run.startedAt()==null?run.createdAt():run.startedAt();return since.isBefore(now.minus(staleAfter));}
    @Transactional(readOnly=true)JobSourceRunResponse get(UUID id){var run=require(id);return map(run,errorResponses(id));}
    @Transactional(readOnly=true)PagedResponse<JobSourceRunResponse>list(UUID sourceId,JobSourceRunStatus status,int page,int requestedSize){Specification<JobSourceRun>spec=(r,q,c)->c.conjunction();if(sourceId!=null)spec=spec.and((r,q,c)->c.equal(r.get("sourceId"),sourceId));if(status!=null)spec=spec.and((r,q,c)->c.equal(r.get("status"),status));var result=runs.findAll(spec,PageRequest.of(Math.max(page,0),Math.min(Math.max(requestedSize,1),100),Sort.by("createdAt").descending().and(Sort.by("id").ascending())));return new PagedResponse<>(result.map(r->map(r,List.of())).getContent(),result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages());}
    private JobSourceRun require(UUID id){return runs.findById(id).orElseThrow(()->new ResourceNotFoundException("Job source run was not found"));}
    private List<JobSourceRunErrorResponse>errorResponses(UUID runId){return errors.findByRunIdOrderByCreatedAtAscIdAsc(runId).stream().map(e->new JobSourceRunErrorResponse(e.id(),e.externalId(),e.safeErrorCode(),e.safeErrorMessage(),e.createdAt())).toList();}
    private static JobSourceRunResponse map(JobSourceRun r,List<JobSourceRunErrorResponse>errors){return new JobSourceRunResponse(r.id(),r.sourceId(),r.triggerType(),r.coverage(),r.searchRuleId(),r.externalEventId(),r.status(),r.checkpoint(),r.startedAt(),r.completedAt(),r.discoveredCount(),r.createdCount(),r.updatedCount(),r.unchangedCount(),r.duplicateCount(),r.failedCount(),r.removedCount(),r.safeErrorCode(),r.safeErrorMessage(),r.createdAt(),errors);}
    private static String safe(String value){return value==null?"UNKNOWN":value.replaceAll("[^A-Z0-9_]","_");}
}
