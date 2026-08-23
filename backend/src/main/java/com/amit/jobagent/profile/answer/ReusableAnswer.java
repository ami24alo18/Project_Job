package com.amit.jobagent.profile.answer;
import com.amit.jobagent.common.error.DomainValidationException;
import com.amit.jobagent.common.persistence.MutableEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="reusable_answer")
class ReusableAnswer extends MutableEntity {
 @Column(name="profile_id",nullable=false,updatable=false)private UUID profileId;
 @Column(nullable=false,length=1000)private String question;
 @Column(name="normalized_question",nullable=false,length=1000)private String normalizedQuestion;
 @Column(nullable=false,columnDefinition="text")private String answer;
 @Enumerated(EnumType.STRING)@Column(nullable=false)private ReusableAnswerCategory category;
 @Enumerated(EnumType.STRING)@Column(nullable=false)private AnswerSensitivity sensitivity;
 @Enumerated(EnumType.STRING)@Column(nullable=false)private ReusableAnswerStatus status;
 @Column(name="verified_at")private Instant verifiedAt;
 protected ReusableAnswer(){}
 ReusableAnswer(UUID profileId,ReusableAnswerRequest r,String normalized,AnswerSensitivity sensitivity){this.profileId=profileId;status=ReusableAnswerStatus.DRAFT;apply(r,normalized,sensitivity);}
 void edit(ReusableAnswerRequest r,String normalized,AnswerSensitivity sensitivity){if(status==ReusableAnswerStatus.ARCHIVED)throw new DomainValidationException("Archived answers must be restored before editing");apply(r,normalized,sensitivity);if(status==ReusableAnswerStatus.VERIFIED){status=ReusableAnswerStatus.DRAFT;verifiedAt=null;}}
 private void apply(ReusableAnswerRequest r,String normalized,AnswerSensitivity sensitivity){question=r.question().trim();normalizedQuestion=normalized;answer=r.answer().trim();category=r.category();this.sensitivity=sensitivity;}
 void verify(){if(status!=ReusableAnswerStatus.DRAFT)throw new DomainValidationException("Only draft answers can be verified");status=ReusableAnswerStatus.VERIFIED;verifiedAt=Instant.now();}
 void archive(){if(status==ReusableAnswerStatus.ARCHIVED)throw new DomainValidationException("Answer is already archived");status=ReusableAnswerStatus.ARCHIVED;verifiedAt=null;}
 void restore(){if(status!=ReusableAnswerStatus.ARCHIVED)throw new DomainValidationException("Only archived answers can be restored");status=ReusableAnswerStatus.DRAFT;verifiedAt=null;}
 UUID profileId(){return profileId;}String question(){return question;}String normalizedQuestion(){return normalizedQuestion;}String answer(){return answer;}ReusableAnswerCategory category(){return category;}AnswerSensitivity sensitivity(){return sensitivity;}ReusableAnswerStatus status(){return status;}Instant verifiedAt(){return verifiedAt;}
}
