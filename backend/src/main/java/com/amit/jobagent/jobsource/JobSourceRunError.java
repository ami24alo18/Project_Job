package com.amit.jobagent.jobsource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity@Table(name="job_source_run_error")
class JobSourceRunError{
    @Id private UUID id;@Column(name="run_id",nullable=false)private UUID runId;@Column(name="external_id",length=300)private String externalId;@Column(name="safe_error_code",nullable=false,length=80)private String safeErrorCode;@Column(name="safe_error_message",nullable=false,length=500)private String safeErrorMessage;@Column(name="created_at",nullable=false)private Instant createdAt;
    protected JobSourceRunError(){}JobSourceRunError(UUID runId,String externalId,String code,String message,Instant now){id=UUID.randomUUID();this.runId=runId;this.externalId=limit(externalId,300);safeErrorCode=limit(code,80);safeErrorMessage=limit(message,500);createdAt=now;}
    private static String limit(String v,int max){return v==null?null:v.substring(0,Math.min(v.length(),max));}
    UUID id(){return id;}String externalId(){return externalId;}String safeErrorCode(){return safeErrorCode;}String safeErrorMessage(){return safeErrorMessage;}Instant createdAt(){return createdAt;}
}
