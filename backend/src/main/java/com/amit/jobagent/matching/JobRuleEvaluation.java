package com.amit.jobagent.matching;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID; import org.hibernate.annotations.JdbcTypeCode; import org.hibernate.type.SqlTypes;
@Entity @Table(name="job_rule_evaluation") class JobRuleEvaluation{
 @Id UUID id;@Column(name="job_id",nullable=false)UUID jobId;@Column(name="profile_version_id",nullable=false)UUID profileVersionId;@Column(name="ruleset_version",nullable=false)String rulesetVersion;@Enumerated(EnumType.STRING)@Column(nullable=false)RuleResult result;
 @JdbcTypeCode(SqlTypes.JSON)@Column(name="reason_codes",nullable=false,columnDefinition="jsonb")String reasonCodes;@JdbcTypeCode(SqlTypes.JSON)@Column(name="safe_evidence",nullable=false,columnDefinition="jsonb")String safeEvidence;
 @JdbcTypeCode(SqlTypes.CHAR)@Column(name="job_content_hash",nullable=false,length=64,columnDefinition="char(64)")String jobContentHash;@JdbcTypeCode(SqlTypes.CHAR)@Column(name="profile_snapshot_checksum",nullable=false,length=64,columnDefinition="char(64)")String profileSnapshotChecksum;@Column(name="created_at",nullable=false)Instant createdAt;
 protected JobRuleEvaluation(){}JobRuleEvaluation(UUID job,UUID profile,String rules,RuleResult result,String reasons,String evidence,String jobHash,String profileHash){id=UUID.randomUUID();jobId=job;profileVersionId=profile;rulesetVersion=rules;this.result=result;reasonCodes=reasons;safeEvidence=evidence;jobContentHash=jobHash;profileSnapshotChecksum=profileHash;createdAt=Instant.now();}
}
