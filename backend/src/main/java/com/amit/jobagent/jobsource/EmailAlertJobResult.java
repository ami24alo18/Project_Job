package com.amit.jobagent.jobsource;
import com.amit.jobagent.job.JobIngestionAction;import java.util.UUID;
public record EmailAlertJobResult(int index,String externalId,UUID jobId,JobIngestionAction action,String errorCode,String errorMessage){}
