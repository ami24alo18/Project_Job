package com.amit.jobagent.profile.version;
import com.amit.jobagent.audit.*;
import com.amit.jobagent.common.error.*;
import com.amit.jobagent.document.ActiveResumeDocumentProvider;
import com.amit.jobagent.document.ResumeDocumentResponse;
import com.amit.jobagent.preference.ActivePreferenceProvider;
import com.amit.jobagent.preference.SearchPreferenceResponse;
import com.amit.jobagent.profile.*;
import com.amit.jobagent.profile.answer.*;
import com.amit.jobagent.resumefact.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ProfileSnapshotService {
 private final CandidateProfileVersionRepository repository;private final ActiveProfileProvider profiles;private final ActivePreferenceProvider preferences;private final VerifiedFactProvider facts;private final VerifiedAnswerProvider answers;private final ActiveResumeDocumentProvider documents;private final AuditService audit;private final ObjectMapper mapper;
 public ProfileSnapshotService(CandidateProfileVersionRepository repository,ActiveProfileProvider profiles,ActivePreferenceProvider preferences,VerifiedFactProvider facts,VerifiedAnswerProvider answers,ActiveResumeDocumentProvider documents,AuditService audit,ObjectMapper mapper){this.repository=repository;this.profiles=profiles;this.preferences=preferences;this.facts=facts;this.answers=answers;this.documents=documents;this.audit=audit;this.mapper=mapper.copy().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY,true).configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);}
 @Transactional public CandidateProfileVersionResponse publish(PublishProfileRequest request){var profile=publishableProfile();var preference=publishablePreferences();var verifiedFacts=facts.verifiedFacts();var verifiedAnswers=answers.verifiedAnswers();var activeDocument=documents.activeMasterResume();validate(profile,preference,verifiedFacts,activeDocument);var content=new SnapshotContent(profile,preference,verifiedFacts,verifiedAnswers,activeDocument.orElse(null));var json=write(content);var checksum=checksum(json);var latest=repository.findFirstByProfileIdOrderByVersionNumberDesc(profile.id());if(latest.isPresent()&&latest.get().checksum().equals(checksum))return map(latest.get(),true);latest.ifPresent(CandidateProfileVersion::deactivate);var version=new CandidateProfileVersion(profile.id(),latest.map(v->v.versionNumber()+1).orElse(1),json,checksum,request.changeReason().trim());var saved=repository.saveAndFlush(version);audit.record(AuditEventType.PROFILE_VERSION_PUBLISHED,"CandidateProfileVersion",saved.id(),"{\"version\":"+saved.versionNumber()+"}");return map(saved,false);}
 @Transactional(readOnly=true)public List<CandidateProfileVersionResponse>list(){var id=profiles.requireProfileId();return repository.findByProfileIdOrderByVersionNumberDesc(id).stream().map(v->map(v,false)).toList();}
 @Transactional(readOnly=true)public CandidateProfileVersionResponse get(int number){var id=profiles.requireProfileId();return map(repository.findByProfileIdAndVersionNumber(id,number).orElseThrow(()->new ResourceNotFoundException("Published profile version was not found")),false);}
 @Transactional(readOnly=true)public CandidateProfileVersionResponse active(){var id=profiles.requireProfileId();return map(repository.findByProfileIdAndActiveTrue(id).orElseThrow(()->new ResourceNotFoundException("No active published profile version exists")),false);}
 private static void validate(CandidateProfileResponse p,SearchPreferenceResponse pref,List<ResumeFactResponse> facts,Optional<ResumeDocumentResponse> doc){var errors=new LinkedHashMap<String,String>();if(p.fullName().isBlank()||p.email().isBlank()||p.professionalTitle().isBlank())errors.put("profile","Core profile is incomplete");if(pref.targetTitles().isEmpty())errors.put("targetTitles","At least one target title is required");if(pref.preferredLocations().isEmpty()&&!pref.remoteAllowed())errors.put("preferredLocations","A preferred location or remote work is required");if(facts.isEmpty())errors.put("resumeFacts","At least one verified resume fact is required");if(doc.isEmpty())errors.put("masterResume","An active master resume is required");if(!errors.isEmpty())throw new PublishValidationException("Candidate data is not ready for publication",errors);}
 private CandidateProfileResponse publishableProfile(){try{return profiles.requireProfile();}catch(ResourceNotFoundException e){throw new PublishValidationException("Candidate data is not ready for publication",Map.of("profile","Core profile has not been configured"));}}
 private SearchPreferenceResponse publishablePreferences(){try{return preferences.requirePreferences();}catch(ResourceNotFoundException e){throw new PublishValidationException("Candidate data is not ready for publication",Map.of("preferences","Search preferences have not been configured"));}}
 private String write(Object value){try{return mapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException("Could not serialize profile snapshot",e);}}
 private static String checksum(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("SHA-256 is unavailable",e);}}
 private CandidateProfileVersionResponse map(CandidateProfileVersion v,boolean unchanged){try{return new CandidateProfileVersionResponse(v.id(),v.versionNumber(),mapper.readTree(v.snapshotJson()),v.checksum(),v.changeReason(),v.active(),v.createdAt(),unchanged);}catch(JsonProcessingException e){throw new IllegalStateException("Stored snapshot is invalid",e);}}
 record SnapshotContent(CandidateProfileResponse profile,SearchPreferenceResponse preferences,List<ResumeFactResponse> verifiedResumeFacts,List<ReusableAnswerResponse> verifiedReusableAnswers,ResumeDocumentResponse activeMasterResume){}
}
