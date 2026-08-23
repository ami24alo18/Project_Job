package com.amit.jobagent.profile;
import com.amit.jobagent.audit.AuditEventType;
import com.amit.jobagent.audit.AuditService;
import com.amit.jobagent.common.error.ConflictException;
import com.amit.jobagent.common.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class CandidateProfileService implements ActiveProfileProvider {
    private final CandidateProfileRepository repository; private final AuditService audit;
    public CandidateProfileService(CandidateProfileRepository repository,AuditService audit){this.repository=repository;this.audit=audit;}
    @Transactional(readOnly=true) public CandidateProfileResponse get(){return map(requireEntity());}
    @Transactional
    public CandidateProfileResponse upsert(CandidateProfileRequest request){
        var existing=repository.findFirstByOrderByCreatedAtAsc();
        if(existing.isEmpty()){
            if(request.recordVersion()!=null) throw new ConflictException("Initial profile creation must not include a record version");
            var saved=repository.saveAndFlush(new CandidateProfile(request)); audit.record(AuditEventType.PROFILE_CREATED,"CandidateProfile",saved.getId(),"{}"); return map(saved);
        }
        var profile=existing.get();
        if(request.recordVersion()==null || request.recordVersion()!=profile.getRecordVersion()) throw new ConflictException("The profile was updated by another request");
        profile.apply(request); var saved=repository.saveAndFlush(profile); audit.record(AuditEventType.PROFILE_UPDATED,"CandidateProfile",saved.getId(),"{}"); return map(saved);
    }
    @Override @Transactional(readOnly=true) public UUID requireProfileId(){return requireEntity().getId();}
    @Override @Transactional(readOnly=true) public CandidateProfileResponse requireProfile(){return map(requireEntity());}
    @Override @Transactional public void lockForGeneration(UUID profileId){repository.findByIdForUpdate(profileId).orElseThrow(()->new ResourceNotFoundException("Candidate profile was not found"));}
    private CandidateProfile requireEntity(){return repository.findFirstByOrderByCreatedAtAsc().orElseThrow(()->new ResourceNotFoundException("Candidate profile has not been created"));}
    static CandidateProfileResponse map(CandidateProfile p){return new CandidateProfileResponse(p.getId(),p.fullName(),p.email(),p.phone(),p.professionalTitle(),p.professionalSummary(),p.currentCompany(),p.currentLocation(),p.totalExperienceMonths(),p.noticePeriodDays(),p.servingNoticePeriod(),p.linkedinUrl(),p.githubUrl(),p.leetcodeUrl(),p.portfolioUrl(),p.getRecordVersion(),p.getCreatedAt(),p.getUpdatedAt());}
}
