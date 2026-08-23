package com.amit.jobagent.resumefact;
import com.amit.jobagent.common.error.DomainValidationException;
import com.amit.jobagent.common.persistence.MutableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
@Entity @Table(name="resume_fact")
class ResumeFact extends MutableEntity {
 @Column(name="profile_id",nullable=false,updatable=false) private UUID profileId;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private ResumeFactCategory category;
 @Column(nullable=false,length=4000) private String statement;
 @Column(length=200) private String company;
 @Column(name="start_date") private LocalDate startDate; @Column(name="end_date") private LocalDate endDate;
 @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="resume_fact_skill_tag",joinColumns=@JoinColumn(name="fact_id")) @Column(name="tag_value") private Set<String> skillTags=new LinkedHashSet<>();
 @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="resume_fact_domain_tag",joinColumns=@JoinColumn(name="fact_id")) @Column(name="tag_value") private Set<String> domainTags=new LinkedHashSet<>();
 @Enumerated(EnumType.STRING) @Column(nullable=false) private ResumeFactStatus status;
 @Enumerated(EnumType.STRING) @Column(name="source_type",nullable=false) private ResumeFactSourceType sourceType;
 @Column(name="source_document_id") private UUID sourceDocumentId;
 @Column(name="source_reference",length=500) private String sourceReference;
 @Column(name="evidence_text",length=4000) private String evidenceText;
 @Column(name="verified_at") private Instant verifiedAt; @Column(name="verified_by",length=150) private String verifiedBy;
 protected ResumeFact(){}
 ResumeFact(UUID profileId,ResumeFactRequest r,ResumeFactSourceType sourceType){this.profileId=profileId;this.sourceType=sourceType;status=ResumeFactStatus.DRAFT;applyValues(r);}
 void edit(ResumeFactRequest r){if(status==ResumeFactStatus.ARCHIVED)throw new DomainValidationException("Archived facts must be restored before editing");applyValues(r);if(status==ResumeFactStatus.VERIFIED){status=ResumeFactStatus.DRAFT;clearVerification();}}
 private void applyValues(ResumeFactRequest r){if(r.endDate()!=null&&r.startDate()!=null&&r.endDate().isBefore(r.startDate()))throw new DomainValidationException("End date cannot be before start date",java.util.Map.of("endDate","Must be on or after start date"));category=r.category();statement=r.statement().trim();company=clean(r.company());startDate=r.startDate();endDate=r.endDate();skillTags=new LinkedHashSet<>(r.skillTags());domainTags=new LinkedHashSet<>(r.domainTags());sourceReference=clean(r.sourceReference());evidenceText=clean(r.evidenceText());}
 void verify(String actor){if(status!=ResumeFactStatus.DRAFT)throw new DomainValidationException("Only draft facts can be verified");status=ResumeFactStatus.VERIFIED;verifiedAt=Instant.now();verifiedBy=actor;}
 void reject(){if(status==ResumeFactStatus.ARCHIVED||status==ResumeFactStatus.REJECTED)throw new DomainValidationException("The fact cannot be rejected from its current status");status=ResumeFactStatus.REJECTED;clearVerification();}
 void restore(){if(status!=ResumeFactStatus.REJECTED&&status!=ResumeFactStatus.ARCHIVED)throw new DomainValidationException("Only rejected or archived facts can be restored");status=ResumeFactStatus.DRAFT;clearVerification();}
 void archive(){if(status==ResumeFactStatus.ARCHIVED)throw new DomainValidationException("The fact is already archived");status=ResumeFactStatus.ARCHIVED;clearVerification();}
 private void clearVerification(){verifiedAt=null;verifiedBy=null;} private static String clean(String v){return v==null||v.isBlank()?null:v.trim();}
 UUID profileId(){return profileId;} ResumeFactCategory category(){return category;} String statement(){return statement;} String company(){return company;} LocalDate startDate(){return startDate;} LocalDate endDate(){return endDate;} Set<String> skillTags(){return Set.copyOf(skillTags);} Set<String> domainTags(){return Set.copyOf(domainTags);} ResumeFactStatus status(){return status;} ResumeFactSourceType sourceType(){return sourceType;} UUID sourceDocumentId(){return sourceDocumentId;} String sourceReference(){return sourceReference;} String evidenceText(){return evidenceText;} Instant verifiedAt(){return verifiedAt;} String verifiedBy(){return verifiedBy;}
}
