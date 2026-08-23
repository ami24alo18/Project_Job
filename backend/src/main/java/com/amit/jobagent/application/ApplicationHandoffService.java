package com.amit.jobagent.application;

import com.amit.jobagent.audit.*;
import com.amit.jobagent.common.api.PagedResponse;
import com.amit.jobagent.common.error.*;
import com.amit.jobagent.job.*;
import com.amit.jobagent.profile.ActiveProfileProvider;
import java.net.URI;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.time.Instant;import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationHandoffService {
    private static final Set<JobPostingStatus> ACTIVE = Set.of(JobPostingStatus.READY_FOR_EVALUATION, JobPostingStatus.NEEDS_REVIEW);
    private final ApplicationHandoffRepository handoffs; private final ApplicationSubmissionReportRepository reports;
    private final ApplicationPackageRepository packages; private final ApplicationPackageRevisionRepository revisions;
    private final ApplicationReviewDecisionRepository decisions; private final GeneratedContentRepository contents;
    private final ApplicationQuestionDraftRepository questions; private final DocumentArtifactRepository artifacts;
    private final ActiveProfileProvider profiles; private final JobHandoffProvider jobs;
    private final HandoffEligibilityService phase6; private final AuditService audit; private final ApplicationTrackerRepository trackers; private final ApplicationTimelineEventRepository timeline;
    ApplicationHandoffService(ApplicationHandoffRepository h, ApplicationSubmissionReportRepository sr, ApplicationPackageRepository p,
            ApplicationPackageRevisionRepository r, ApplicationReviewDecisionRepository d, GeneratedContentRepository c,
            ApplicationQuestionDraftRepository q, DocumentArtifactRepository a, ActiveProfileProvider ap, JobHandoffProvider j,
            HandoffEligibilityService e, AuditService au, ApplicationTrackerRepository tr, ApplicationTimelineEventRepository tl) { handoffs=h;reports=sr;packages=p;revisions=r;decisions=d;contents=c;questions=q;artifacts=a;profiles=ap;jobs=j;phase6=e;audit=au;trackers=tr;timeline=tl; }

    @Transactional public ApplicationHandoffResponse create(UUID packageId, UUID revisionId, String key) {
        requireKey(key); var p=ownedPackage(packageId); var r=revision(p, revisionId);
        String hash=hash(profiles.requireProfileId()+"|"+packageId+"|"+revisionId+"|CREATE|"+key.trim());
        var prior=handoffs.findByIdempotencyHash(hash).orElse(null); if(prior!=null) return map(prior);
        var eligibility=creationEligibility(p,r); if(!eligibility.eligible()) throw new DomainValidationException("Handoff blocked: "+messages(eligibility));
        var job=jobs.require(p.jobId); var approval=activeApproval(r.id);
        var handoff=handoffs.saveAndFlush(new ApplicationHandoff(p.getId(),r.id,p.jobId,approval.id,job.canonicalApplyUrl(),hash(job.canonicalApplyUrl()),r.jobChecksum,r.profileChecksum,r.evaluationChecksum,approval.artifactChecksum,hash,actor()));
        reports.save(new ApplicationSubmissionReport(handoff.id,actor(),SubmissionAction.HANDOFF_CREATED,null,null,null,null));
        audit.record(AuditEventType.APPLICATION_HANDOFF_CREATED,"ApplicationHandoff",handoff.id,"{\"revisionId\":\""+r.id+"\"}");
        return map(handoff);
    }
    @Transactional(readOnly=true) public PagedResponse<ApplicationHandoffResponse> list(int page,int size) {
        if(page<0||size<1||size>100) throw new DomainValidationException("Page must be non-negative and size between 1 and 100");
        var ids=packages.findByCandidateIdOrderByCreatedAtDesc(profiles.requireProfileId()).stream().map(ApplicationPackage::getId).toList();
        var all=ids.isEmpty()?List.<ApplicationHandoff>of():handoffs.findByPackageIdInOrderByCreatedAtDesc(ids); int start=Math.min(page*size,all.size()), end=Math.min(start+size,all.size());
        return new PagedResponse<>(all.subList(start,end).stream().map(this::map).toList(),page,size,all.size(),(all.size()+size-1)/size);
    }
    @Transactional(readOnly=true) public ApplicationHandoffResponse get(UUID id){return map(owned(id));}
    @Transactional(readOnly=true) public HandoffEligibilityResponse eligibility(UUID id){return actionEligibility(owned(id));}
    @Transactional(readOnly=true) public SubmissionKitResponse kit(UUID id) {
        var h=owned(id); var p=ownedPackage(h.packageId); var r=revision(p,h.revisionId); var job=jobs.require(h.jobId);
        var approval=decisions.findById(h.reviewDecisionId).orElseThrow(); var cs=contents.findByRevisionIdOrderByOrderAscIdAsc(r.id);
        var answers=new ArrayList<SubmissionKitResponse.SubmissionAnswer>(); var warnings=new ArrayList<String>();
        for(var q:questions.findByRevisionIdOrderByCreatedAtAscIdAsc(r.id)) {
            if(q.classification==QuestionClassification.SENSITIVE_NEVER_AUTOMATIC||q.classification==QuestionClassification.USER_INPUT_REQUIRED||q.answer==null) warnings.add(q.question+" — manual completion required ("+q.classification+")");
            else answers.add(new SubmissionKitResponse.SubmissionAnswer(q.id,q.question,q.answer,q.classification));
        }
        var resume=artifacts.findByRevisionId(r.id).stream().filter(a->a.type==ArtifactType.PDF_RESUME||a.type==ArtifactType.DOCX_RESUME).map(a->new ArtifactResponse(a.id,a.type,a.fileName,a.contentType,a.size,a.checksum,a.templateVersion,a.createdAt)).toList();
        return new SubmissionKitResponse(h.id,job.title(),job.company(),job.source(),h.applyUrl,job.expiresAt(),r.revisionNumber,approval.createdAt,resume,text(cs,GeneratedContentType.COVER_LETTER),text(cs,GeneratedContentType.RECRUITER_MESSAGE),List.copyOf(answers),List.copyOf(warnings),List.of("Correct resume selected","Content reviewed","Unanswered questions noted","Sensitive questions require manual completion","External application will be submitted manually"),actionEligibility(h));
    }
    @Transactional public LaunchResponse launch(UUID id,HandoffStateRequest req,String key){var h=change(id,req,key,SubmissionAction.OPENED_EXTERNALLY,ApplicationHandoffStatus.OPENED_EXTERNALLY,false);return new LaunchResponse(map(h),h.applyUrl,"Opening the application website does not submit an application");}
    @Transactional public ApplicationHandoffResponse submitted(UUID id,HandoffStateRequest req,String key){var h=change(id,req,key,SubmissionAction.SUBMITTED_REPORTED_BY_USER,ApplicationHandoffStatus.SUBMITTED_REPORTED_BY_USER,true);if(trackers.findByHandoffId(h.id).isEmpty()){var r=revision(ownedPackage(h.packageId),h.revisionId);var tracker=trackers.save(new ApplicationTracker(h,r,actor()));timeline.save(new ApplicationTimelineEvent(tracker.id,"SUBMISSION_REPORTED",null,ApplicationTrackingStatus.SUBMITTED,"USER_REPORTED",null,actor()));}return map(h);}
    @Transactional public ApplicationHandoffResponse notSubmitted(UUID id,HandoffStateRequest req,String key){return map(change(id,req,key,SubmissionAction.NOT_SUBMITTED,ApplicationHandoffStatus.NOT_SUBMITTED,false));}
    @Transactional public ApplicationHandoffResponse cancel(UUID id,HandoffStateRequest req,String key){return map(change(id,req,key,SubmissionAction.CANCELLED,ApplicationHandoffStatus.CANCELLED,false));}
    private ApplicationHandoff change(UUID id,HandoffStateRequest req,String key,SubmissionAction action,ApplicationHandoffStatus next,boolean submitted){
        requireKey(key); var h=owned(id); String hash=hash(profiles.requireProfileId()+"|"+id+"|"+action+"|"+key.trim());
        if(reports.findByIdempotencyHash(hash).isPresent()) return h;
        if(h.recordVersion!=req.recordVersion()) throw new ConflictException("Handoff changed; reload before continuing");
        var eligibility=actionEligibility(h); if(!eligibility.eligible()) throw new DomainValidationException("Handoff blocked: "+messages(eligibility));
        if(action==SubmissionAction.SUBMITTED_REPORTED_BY_USER&&h.launchedAt==null) throw new DomainValidationException("Open the external application site before reporting submission");
        if(h.status==ApplicationHandoffStatus.SUBMITTED_REPORTED_BY_USER||h.status==ApplicationHandoffStatus.CANCELLED||h.status==ApplicationHandoffStatus.NOT_SUBMITTED) throw new ConflictException("This handoff is already in a terminal state");
        h.transition(next); handoffs.saveAndFlush(h); reports.save(new ApplicationSubmissionReport(h.id,actor(),action,safe(req.externalReference(),200),safe(req.note(),1000),submitted?Instant.now():null,hash));
        audit.record(event(action),"ApplicationHandoff",h.id,"{\"action\":\""+action+"\"}"); return h;
    }
    private HandoffEligibilityResponse creationEligibility(ApplicationPackage p,ApplicationPackageRevision r){var reasons=new ArrayList<>(phase6.check(p.getId(),r.id).blockingReasons());jobReasons(jobs.require(p.jobId),reasons);return new HandoffEligibilityResponse(reasons.isEmpty(),List.copyOf(reasons));}
    private HandoffEligibilityResponse actionEligibility(ApplicationHandoff h){var p=ownedPackage(h.packageId);var r=revision(p,h.revisionId);var reasons=new ArrayList<>(phase6.check(p.getId(),r.id).blockingReasons());var job=jobs.require(h.jobId);jobReasons(job,reasons);if(!Objects.equals(job.canonicalApplyUrl(),h.applyUrl)||!Objects.equals(hash(h.applyUrl),h.applyUrlChecksum))HandoffEligibilityService.block(reasons,"APPLY_URL_CHANGED","The canonical application URL changed after handoff creation");if(!Objects.equals(r.jobChecksum,h.jobChecksum)||!Objects.equals(r.profileChecksum,h.profileChecksum)||!Objects.equals(r.evaluationChecksum,h.evaluationChecksum))HandoffEligibilityService.block(reasons,"HANDOFF_SOURCE_SNAPSHOT_CHANGED","The approved source snapshot no longer matches");if(!Objects.equals(phase6.check(p.getId(),r.id).artifactManifestChecksum(),h.artifactChecksum))HandoffEligibilityService.block(reasons,"HANDOFF_ARTIFACT_SNAPSHOT_CHANGED","The approved artifact manifest no longer matches");return new HandoffEligibilityResponse(reasons.isEmpty(),List.copyOf(reasons));}
    private void jobReasons(JobHandoffSnapshot job,List<BlockingReason> reasons){if(!ACTIVE.contains(job.status()))HandoffEligibilityService.block(reasons,"JOB_NOT_ACTIVE","The job is no longer active");if(job.expiresAt()!=null&&job.expiresAt().isBefore(Instant.now()))HandoffEligibilityService.block(reasons,"JOB_DEADLINE_PASSED","The application deadline has passed");if(!validUrl(job.canonicalApplyUrl()))HandoffEligibilityService.block(reasons,"INVALID_APPLY_URL","A valid canonical HTTP(S) application URL is required");}
    static boolean validUrl(String value){try{var u=URI.create(value);return ("https".equalsIgnoreCase(u.getScheme())||"http".equalsIgnoreCase(u.getScheme()))&&u.getHost()!=null&&u.getUserInfo()==null&&!u.isOpaque();}catch(Exception ignored){return false;}}
    private ApplicationHandoffResponse map(ApplicationHandoff h){var p=ownedPackage(h.packageId);var r=revision(p,h.revisionId);var job=jobs.require(h.jobId);var approval=decisions.findById(h.reviewDecisionId).orElseThrow();var activity=reports.findByHandoffIdOrderByCreatedAtAsc(h.id).stream().map(x->new SubmissionActivityResponse(x.id,x.action,x.actor,x.externalReference,x.note,x.reportedAt,x.createdAt)).toList();return new ApplicationHandoffResponse(h.id,h.packageId,h.revisionId,r.revisionNumber,h.jobId,h.reviewDecisionId,job.title(),job.company(),job.source(),h.applyUrl,job.expiresAt(),h.status,h.createdBy,approval.createdAt,h.createdAt,h.updatedAt,h.launchedAt,h.submissionReportedAt,h.recordVersion,actionEligibility(h),activity);}
    private ApplicationPackage ownedPackage(UUID id){UUID candidate=profiles.requireProfileId();return packages.findById(id).filter(x->x.candidateId.equals(candidate)).orElseThrow(()->new ResourceNotFoundException("Application package was not found"));}
    private ApplicationHandoff owned(UUID id){var h=handoffs.findById(id).orElseThrow(()->new ResourceNotFoundException("Application handoff was not found"));ownedPackage(h.packageId);return h;}
    private ApplicationPackageRevision revision(ApplicationPackage p,UUID id){return revisions.findByPackageIdAndId(p.getId(),id).orElseThrow(()->new ResourceNotFoundException("Package revision was not found"));}
    private ApplicationReviewDecision activeApproval(UUID revision){return decisions.findByRevisionIdOrderByCreatedAtDesc(revision).stream().filter(x->x.decision==ReviewDecisionType.APPROVED_FOR_HANDOFF&&x.invalidatedAt==null).findFirst().orElseThrow(()->new DomainValidationException("An active approval is required"));}
    private static String text(List<GeneratedContent> c,GeneratedContentType type){return c.stream().filter(x->x.type==type).map(x->x.text).findFirst().orElse(null);} private static void requireKey(String k){if(k==null||k.isBlank()||k.length()>200)throw new DomainValidationException("A valid Idempotency-Key is required");} private static String safe(String x,int max){if(x==null)return null;var v=x.replaceAll("[<>]","").replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]","").trim();return v.isEmpty()?null:v.substring(0,Math.min(max,v.length()));} private static String messages(HandoffEligibilityResponse e){return e.blockingReasons().stream().map(BlockingReason::message).reduce((a,b)->a+"; "+b).orElse("ineligible");} private static String actor(){var a=SecurityContextHolder.getContext().getAuthentication();return a==null?"system":a.getName();} private static String hash(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception x){throw new IllegalStateException(x);}} private static AuditEventType event(SubmissionAction a){return switch(a){case OPENED_EXTERNALLY->AuditEventType.APPLICATION_HANDOFF_LAUNCHED;case SUBMITTED_REPORTED_BY_USER->AuditEventType.APPLICATION_SUBMISSION_REPORTED;case NOT_SUBMITTED->AuditEventType.APPLICATION_NOT_SUBMITTED;case CANCELLED->AuditEventType.APPLICATION_HANDOFF_CANCELLED;default->AuditEventType.APPLICATION_HANDOFF_BLOCKED;};}
}
