package com.amit.jobagent.preference;
import com.amit.jobagent.audit.AuditEventType;
import com.amit.jobagent.audit.AuditService;
import com.amit.jobagent.common.error.ConflictException;
import com.amit.jobagent.common.error.DomainValidationException;
import com.amit.jobagent.common.error.ResourceNotFoundException;
import com.amit.jobagent.profile.ActiveProfileProvider;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class SearchPreferenceService implements ActivePreferenceProvider {
 private final SearchPreferenceRepository repository;private final ActiveProfileProvider profiles;private final AuditService audit;
 public SearchPreferenceService(SearchPreferenceRepository repository,ActiveProfileProvider profiles,AuditService audit){this.repository=repository;this.profiles=profiles;this.audit=audit;}
 @Transactional(readOnly=true) public SearchPreferenceResponse get(){return requirePreferences();}
 @Transactional public SearchPreferenceResponse upsert(SearchPreferenceRequest raw){
   var request=normalize(raw);validate(request);var profileId=profiles.requireProfileId();var existing=repository.findByProfileId(profileId);
   SearchPreference preference;
   if(existing.isEmpty()){if(request.recordVersion()!=null)throw new ConflictException("Initial preferences must not include a record version");preference=new SearchPreference(profileId,request);}
   else{preference=existing.get();if(request.recordVersion()==null||request.recordVersion()!=preference.getRecordVersion())throw new ConflictException("Preferences were updated by another request");preference.apply(request);}
   var saved=repository.saveAndFlush(preference);audit.record(AuditEventType.PREFERENCES_UPDATED,"SearchPreference",saved.getId(),"{}");return map(saved);
 }
 @Override @Transactional(readOnly=true) public SearchPreferenceResponse requirePreferences(){var id=profiles.requireProfileId();return repository.findByProfileId(id).map(SearchPreferenceService::map).orElseThrow(()->new ResourceNotFoundException("Search preferences have not been configured"));}
 static SearchPreferenceRequest normalize(SearchPreferenceRequest r){return new SearchPreferenceRequest(norm(r.targetTitles()),norm(r.preferredLocations()),norm(r.excludedLocations()),norm(r.requiredSkills()),norm(r.preferredSkills()),norm(r.excludedCompanies()),norm(r.excludedKeywords()),r.minimumExperienceYears(),r.maximumExperienceYears(),r.minimumMatchScore(),r.maximumDailyShortlist(),r.maximumDailyApplications(),r.remoteAllowed(),r.hybridAllowed(),r.onsiteAllowed(),r.recordVersion());}
 static Set<String> norm(Set<String> values){var unique=new LinkedHashMap<String,String>();for(var value:values){if(value!=null&&!value.isBlank()){var trimmed=value.trim();unique.putIfAbsent(trimmed.toLowerCase(Locale.ROOT),trimmed);}}return new LinkedHashSet<>(unique.values());}
 static void validate(SearchPreferenceRequest r){var errors=new LinkedHashMap<String,String>();if(r.targetTitles().isEmpty())errors.put("targetTitles","At least one target title is required");if(r.preferredLocations().isEmpty()&&!r.remoteAllowed())errors.put("preferredLocations","At least one preferred location or remote work is required");if(r.minimumExperienceYears()>r.maximumExperienceYears())errors.put("maximumExperienceYears","Must be greater than or equal to minimum experience");if(!r.remoteAllowed()&&!r.hybridAllowed()&&!r.onsiteAllowed())errors.put("workplaceTypes","At least one workplace type must be allowed");if(overlap(r.requiredSkills(),r.preferredSkills()))errors.put("preferredSkills","A skill cannot be both required and preferred");if(overlap(r.preferredLocations(),r.excludedLocations()))errors.put("excludedLocations","A location cannot be both preferred and excluded");if(!errors.isEmpty())throw new DomainValidationException("Search preferences are inconsistent",errors);}
 private static boolean overlap(Set<String>a,Set<String>b){var lowered=a.stream().map(v->v.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());return b.stream().map(v->v.toLowerCase(Locale.ROOT)).anyMatch(lowered::contains);}
 static SearchPreferenceResponse map(SearchPreference p){return new SearchPreferenceResponse(p.profileId(),p.targetTitles(),p.preferredLocations(),p.excludedLocations(),p.requiredSkills(),p.preferredSkills(),p.excludedCompanies(),p.excludedKeywords(),p.minimumExperienceYears(),p.maximumExperienceYears(),p.minimumMatchScore(),p.maximumDailyShortlist(),p.maximumDailyApplications(),p.remoteAllowed(),p.hybridAllowed(),p.onsiteAllowed(),p.getRecordVersion(),p.getCreatedAt(),p.getUpdatedAt());}
}
