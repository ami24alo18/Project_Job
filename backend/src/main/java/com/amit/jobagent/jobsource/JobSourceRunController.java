package com.amit.jobagent.jobsource;

import com.amit.jobagent.common.api.PagedResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController@SecurityRequirement(name="basicAuth")
public class JobSourceRunController{
    private final JobSourceSyncCommandService commands;private final JobSourceRunPersistenceService runs;
    public JobSourceRunController(JobSourceSyncCommandService commands,JobSourceRunPersistenceService runs){this.commands=commands;this.runs=runs;}
    @PostMapping("/api/v1/job-sources/{sourceId}/sync")@ResponseStatus(HttpStatus.ACCEPTED)public SyncRunResponse sync(@PathVariable UUID sourceId){return commands.queue(sourceId,JobSourceTriggerType.MANUAL);}
    @PostMapping("/api/v1/job-sources/sync-enabled")@ResponseStatus(HttpStatus.ACCEPTED)public SyncEnabledResponse syncEnabled(){return commands.queueEnabled();}
    @GetMapping("/api/v1/job-source-runs")public PagedResponse<JobSourceRunResponse>list(@RequestParam(required=false)UUID sourceId,@RequestParam(required=false)JobSourceRunStatus status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return runs.list(sourceId,status,page,size);}
    @GetMapping("/api/v1/job-source-runs/{runId}")public JobSourceRunResponse get(@PathVariable UUID runId){return runs.get(runId);}
    @GetMapping("/api/v1/job-sources/{sourceId}/runs")public PagedResponse<JobSourceRunResponse>sourceRuns(@PathVariable UUID sourceId,@RequestParam(required=false)JobSourceRunStatus status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return runs.list(sourceId,status,page,size);}
}
