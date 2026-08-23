package com.amit.jobagent.profile.answer;
import com.amit.jobagent.audit.*;
import com.amit.jobagent.common.error.*;
import com.amit.jobagent.profile.ActiveProfileProvider;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ReusableAnswerService implements VerifiedAnswerProvider {
 private final ReusableAnswerRepository repository;private final ActiveProfileProvider profiles;private final AuditService audit;
 public ReusableAnswerService(ReusableAnswerRepository repository,ActiveProfileProvider profiles,AuditService audit){this.repository=repository;this.profiles=profiles;this.audit=audit;}
 @Transactional public ReusableAnswerResponse create(ReusableAnswerRequest r){var profileId=profiles.requireProfileId();var normalized=normalizeQuestion(r.question());if(repository.findByProfileIdAndNormalizedQuestion(profileId,normalized).isPresent())throw new ConflictException("An answer already exists for this question");var saved=repository.saveAndFlush(new ReusableAnswer(profileId,r,normalized,sensitivity(r)));return map(saved);}
 @Transactional(readOnly=true) public List<ReusableAnswerResponse>list(ReusableAnswerCategory category,AnswerSensitivity sensitivity,ReusableAnswerStatus status){return repository.findByProfileIdOrderByCreatedAtDescIdAsc(profiles.requireProfileId()).stream().filter(a->category==null||a.category()==category).filter(a->sensitivity==null||a.sensitivity()==sensitivity).filter(a->status==null||a.status()==status).map(ReusableAnswerService::map).toList();}
 @Transactional(readOnly=true)public ReusableAnswerResponse get(UUID id){return map(requireOwned(id));}
 @Transactional public ReusableAnswerResponse update(UUID id,ReusableAnswerRequest r){var answer=requireOwned(id);checkVersion(answer,r.recordVersion());var normalized=normalizeQuestion(r.question());repository.findByProfileIdAndNormalizedQuestion(answer.profileId(),normalized).filter(other->!other.getId().equals(id)).ifPresent(other->{throw new ConflictException("An answer already exists for this question");});answer.edit(r,normalized,sensitivity(r));return map(repository.saveAndFlush(answer));}
 @Transactional public ReusableAnswerResponse verify(UUID id){var answer=requireOwned(id);answer.verify();var saved=repository.saveAndFlush(answer);audit.record(AuditEventType.REUSABLE_ANSWER_VERIFIED,"ReusableAnswer",id,"{}");return map(saved);}
 @Transactional public ReusableAnswerResponse archive(UUID id){var answer=requireOwned(id);answer.archive();return map(repository.saveAndFlush(answer));}
 @Transactional public ReusableAnswerResponse restore(UUID id){var answer=requireOwned(id);answer.restore();return map(repository.saveAndFlush(answer));}
 @Override @Transactional(readOnly=true)public List<ReusableAnswerResponse>verifiedAnswers(){return repository.findByProfileIdAndStatusOrderByCreatedAtAscIdAsc(profiles.requireProfileId(),ReusableAnswerStatus.VERIFIED).stream().map(ReusableAnswerService::map).toList();}
 private ReusableAnswer requireOwned(UUID id){var profileId=profiles.requireProfileId();var a=repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Reusable answer was not found"));if(!a.profileId().equals(profileId))throw new ResourceNotFoundException("Reusable answer was not found");return a;}
 private static AnswerSensitivity sensitivity(ReusableAnswerRequest r){var value=r.sensitivity();if(value==null)value=switch(r.category()){case WORK_AUTHORIZATION,RELOCATION->AnswerSensitivity.REQUIRES_REVIEW;default->AnswerSensitivity.REQUIRES_REVIEW;};if(value==AnswerSensitivity.SAFE_AUTOFILL&&(r.category()==ReusableAnswerCategory.COMPENSATION||r.category()==ReusableAnswerCategory.DEMOGRAPHIC||r.category()==ReusableAnswerCategory.LEGAL))throw new DomainValidationException("Sensitive answer categories cannot be safe for autofill",java.util.Map.of("sensitivity","Use REQUIRES_REVIEW or NEVER_AUTOFILL"));return value;}
 static String normalizeQuestion(String q){return Normalizer.normalize(q,Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
 private static void checkVersion(ReusableAnswer a,Long v){if(v==null||v!=a.getRecordVersion())throw new ConflictException("Reusable answer was updated by another request");}
 static ReusableAnswerResponse map(ReusableAnswer a){return new ReusableAnswerResponse(a.getId(),a.profileId(),a.question(),a.normalizedQuestion(),a.answer(),a.category(),a.sensitivity(),a.status(),a.verifiedAt(),a.getRecordVersion(),a.getCreatedAt(),a.getUpdatedAt());}
}
