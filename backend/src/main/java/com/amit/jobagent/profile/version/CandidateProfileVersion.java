package com.amit.jobagent.profile.version;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="candidate_profile_version")
class CandidateProfileVersion {
 @Id private UUID id;
 @Column(name="profile_id",nullable=false,updatable=false)private UUID profileId;
 @Column(name="version_number",nullable=false,updatable=false)private int versionNumber;
 @JdbcTypeCode(SqlTypes.JSON)@Column(name="snapshot_json",nullable=false,updatable=false,columnDefinition="jsonb")private String snapshotJson;
 @JdbcTypeCode(SqlTypes.CHAR)@Column(nullable=false,updatable=false,length=64,columnDefinition="char(64)")private String checksum;
 @Column(name="change_reason",nullable=false,updatable=false,length=500)private String changeReason;
 @Column(nullable=false)private boolean active;
 @Column(name="created_at",nullable=false,updatable=false)private Instant createdAt;
 protected CandidateProfileVersion(){}
 CandidateProfileVersion(UUID profileId,int number,String json,String checksum,String reason){id=UUID.randomUUID();this.profileId=profileId;versionNumber=number;snapshotJson=json;this.checksum=checksum;changeReason=reason;active=true;createdAt=Instant.now();}
 void deactivate(){active=false;}UUID id(){return id;}UUID profileId(){return profileId;}int versionNumber(){return versionNumber;}String snapshotJson(){return snapshotJson;}String checksum(){return checksum;}String changeReason(){return changeReason;}boolean active(){return active;}Instant createdAt(){return createdAt;}
}
