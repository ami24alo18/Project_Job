package com.amit.jobagent.matching;

import com.amit.jobagent.common.persistence.MutableEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="matching_configuration")
class MatchingConfiguration extends MutableEntity {
 @Column(name="profile_id",nullable=false,unique=true,updatable=false) UUID profileId;
 @Column(name="skills_weight",nullable=false) int skillsWeight=35; @Column(name="experience_weight",nullable=false) int experienceWeight=20;
 @Column(name="role_weight",nullable=false) int roleWeight=15; @Column(name="location_weight",nullable=false) int locationWeight=15;
 @Column(name="domain_weight",nullable=false) int domainWeight=10; @Column(name="compensation_weight",nullable=false) int compensationWeight=5;
 @Column(name="strong_apply_threshold",nullable=false) int strongApplyThreshold=85; @Column(name="apply_threshold",nullable=false) int applyThreshold=75;
 @Column(name="manual_review_threshold",nullable=false) int manualReviewThreshold=60;
 @Column(name="maximum_allowed_experience_gap",nullable=false) int maximumAllowedExperienceGap=1;
 @Column(name="maximum_jobs_per_batch",nullable=false) int maximumJobsPerBatch=25;
 @Column(name="maximum_daily_ai_requests",nullable=false) int maximumDailyAiRequests=100;
 @Column(name="maximum_daily_input_tokens",nullable=false) long maximumDailyInputTokens=500000;
 @Column(name="ruleset_version",nullable=false,length=40) String rulesetVersion="v1";
 protected MatchingConfiguration(){} MatchingConfiguration(UUID profileId){this.profileId=profileId;}
 void apply(MatchingConfigurationRequest r){skillsWeight=r.skillsWeight();experienceWeight=r.experienceWeight();roleWeight=r.roleWeight();locationWeight=r.locationWeight();domainWeight=r.domainWeight();compensationWeight=r.compensationWeight();strongApplyThreshold=r.strongApplyThreshold();applyThreshold=r.applyThreshold();manualReviewThreshold=r.manualReviewThreshold();maximumAllowedExperienceGap=r.maximumAllowedExperienceGap();maximumJobsPerBatch=r.maximumJobsPerBatch();maximumDailyAiRequests=r.maximumDailyAiRequests();maximumDailyInputTokens=r.maximumDailyInputTokens();rulesetVersion=r.rulesetVersion().trim();}
}
