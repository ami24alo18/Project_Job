package com.amit.jobagent.resumefact;
import com.amit.jobagent.audit.AuditEventType;
import com.amit.jobagent.audit.AuditService;
import com.amit.jobagent.common.api.PagedResponse;
import com.amit.jobagent.common.error.ConflictException;
import com.amit.jobagent.common.error.DomainValidationException;
import com.amit.jobagent.common.error.ResourceNotFoundException;
import com.amit.jobagent.profile.ActiveProfileProvider;
import jakarta.persistence.criteria.JoinType;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ResumeFactService implements VerifiedFactProvider {
 private final ResumeFactRepository repository;private final ActiveProfileProvider profiles;private final AuditService audit;
 public ResumeFactService(ResumeFactRepository repository,ActiveProfileProvider profiles,AuditService audit){this.repository=repository;this.profiles=profiles;this.audit=audit;}
 static Set<String> normalize(Set<String> values){var map=new LinkedHashMap<String,String>();for(var v:values)if(v!=null&&!v.isBlank()){var t=v.trim();map.putIfAbsent(t.toLowerCase(Locale.ROOT),t);}return new LinkedHashSet<>(map.values());}
 @Transactional public ResumeFactResponse create(ResumeFactRequest request){validateDates(request);var fact=repository.saveAndFlush(new ResumeFact(profiles.requireProfileId(),request,ResumeFactSourceType.MANUAL));audit.record(AuditEventType.RESUME_FACT_CREATED,"ResumeFact",fact.getId(),"{}");return map(fact);}
 @Transactional(readOnly=true) public ResumeFactResponse get(UUID id){return map(requireOwned(id));}
 @Transactional(readOnly=true) public PagedResponse<ResumeFactResponse> list(int page,int requestedSize,ResumeFactStatus status,ResumeFactCategory category,String skill,String domain,String search){int size=Math.min(Math.max(requestedSize,1),100);var profileId=profiles.requireProfileId();Specification<ResumeFact> spec=(root,q,cb)->cb.equal(root.get("profileId"),profileId);if(status!=null)spec=spec.and((r,q,c)->c.equal(r.get("status"),status));if(category!=null)spec=spec.and((r,q,c)->c.equal(r.get("category"),category));if(search!=null&&!search.isBlank()){var term="%"+search.trim().toLowerCase(Locale.ROOT)+"%";spec=spec.and((r,q,c)->c.or(c.like(c.lower(r.get("statement")),term),c.like(c.lower(r.get("company")),term)));}if(skill!=null&&!skill.isBlank())spec=spec.and(tagSpec("skillTags",skill));if(domain!=null&&!domain.isBlank())spec=spec.and(tagSpec("domainTags",domain));var result=repository.findAll(spec,PageRequest.of(Math.max(page,0),size,Sort.by("createdAt").descending().and(Sort.by("id").ascending())));return new PagedResponse<>(result.map(ResumeFactService::map).getContent(),result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages());}
 private static Specification<ResumeFact>tagSpec(String name,String value){return(r,q,c)->{q.distinct(true);return c.equal(c.lower(r.join(name,JoinType.LEFT)),value.trim().toLowerCase(Locale.ROOT));};}
 @Transactional public ResumeFactResponse update(UUID id,ResumeFactRequest r){validateDates(r);var fact=requireOwned(id);checkVersion(fact,r.recordVersion());fact.edit(r);var saved=repository.saveAndFlush(fact);audit.record(AuditEventType.RESUME_FACT_UPDATED,"ResumeFact",id,"{}");return map(saved);}
 @Transactional public ResumeFactResponse verify(UUID id){var fact=requireOwned(id);fact.verify(SecurityContextHolder.getContext().getAuthentication().getName());var saved=repository.saveAndFlush(fact);audit.record(AuditEventType.RESUME_FACT_VERIFIED,"ResumeFact",id,"{}");return map(saved);}
 @Transactional public ResumeFactResponse reject(UUID id){var fact=requireOwned(id);fact.reject();var saved=repository.saveAndFlush(fact);audit.record(AuditEventType.RESUME_FACT_REJECTED,"ResumeFact",id,"{}");return map(saved);}
 @Transactional public ResumeFactResponse restore(UUID id){var fact=requireOwned(id);fact.restore();return map(repository.saveAndFlush(fact));}
 @Transactional public ResumeFactResponse archive(UUID id){var fact=requireOwned(id);fact.archive();var saved=repository.saveAndFlush(fact);audit.record(AuditEventType.RESUME_FACT_ARCHIVED,"ResumeFact",id,"{}");return map(saved);}
 @Transactional public ResumeFactImportResponse importFacts(List<ResumeFactRequest> requests){if(requests.isEmpty()||requests.size()>200)throw new DomainValidationException("Import must contain between 1 and 200 facts");var profileId=profiles.requireProfileId();var existing=repository.findByProfileId(profileId);var ids=new java.util.ArrayList<UUID>();var duplicates=new java.util.ArrayList<Integer>();for(int i=0;i<requests.size();i++){var r=requests.get(i);validateDates(r);boolean duplicate=existing.stream().anyMatch(f->exact(f,r));if(duplicate){duplicates.add(i);continue;}var saved=repository.save(new ResumeFact(profileId,r,ResumeFactSourceType.STRUCTURED_IMPORT));existing.add(saved);ids.add(saved.getId());audit.record(AuditEventType.RESUME_FACT_CREATED,"ResumeFact",saved.getId(),"{\"source\":\"STRUCTURED_IMPORT\"}");}repository.flush();return new ResumeFactImportResponse(ids,duplicates,requests.size(),ids.size());}
 private static boolean exact(ResumeFact f,ResumeFactRequest r){return f.category()==r.category()&&f.statement().equalsIgnoreCase(r.statement().trim())&&java.util.Objects.equals(f.company(),clean(r.company()))&&java.util.Objects.equals(f.startDate(),r.startDate())&&java.util.Objects.equals(f.endDate(),r.endDate())&&f.skillTags().equals(r.skillTags())&&f.domainTags().equals(r.domainTags())&&java.util.Objects.equals(f.evidenceText(),clean(r.evidenceText()));}
 private static String clean(String v){return v==null||v.isBlank()?null:v.trim();}private static void validateDates(ResumeFactRequest r){if(r.startDate()!=null&&r.endDate()!=null&&r.endDate().isBefore(r.startDate()))throw new DomainValidationException("End date cannot be before start date",java.util.Map.of("endDate","Must be on or after start date"));}
 private ResumeFact requireOwned(UUID id){var profileId=profiles.requireProfileId();var f=repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Resume fact was not found"));if(!f.profileId().equals(profileId))throw new ResourceNotFoundException("Resume fact was not found");return f;}
 private static void checkVersion(ResumeFact f,Long version){if(version==null||version!=f.getRecordVersion())throw new ConflictException("Resume fact was updated by another request");}
 @Override @Transactional(readOnly=true) public List<ResumeFactResponse> verifiedFacts(){return repository.findByProfileIdAndStatusOrderByCreatedAtAscIdAsc(profiles.requireProfileId(),ResumeFactStatus.VERIFIED).stream().map(ResumeFactService::map).toList();}
 static ResumeFactResponse map(ResumeFact f){return new ResumeFactResponse(f.getId(),f.profileId(),f.category(),f.statement(),f.company(),f.startDate(),f.endDate(),f.skillTags(),f.domainTags(),f.status(),f.sourceType(),f.sourceDocumentId(),f.sourceReference(),f.evidenceText(),f.verifiedAt(),f.verifiedBy(),f.getRecordVersion(),f.getCreatedAt(),f.getUpdatedAt());}
}
