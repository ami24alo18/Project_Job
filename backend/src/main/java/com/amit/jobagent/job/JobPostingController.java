package com.amit.jobagent.job;

import com.amit.jobagent.common.api.PagedResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/jobs") @SecurityRequirement(name="basicAuth")
public class JobPostingController {
    private final JobPostingService service;public JobPostingController(JobPostingService service){this.service=service;}
    @PostMapping("/manual")public JobPostingResponse manual(@Valid@RequestBody ManualJobRequest request,@RequestHeader(value="Idempotency-Key",required=false)String key){return service.createManual(request,key);}
    @GetMapping public PagedResponse<JobPostingResponse>list(@RequestParam(required=false)UUID sourceId,@RequestParam(required=false)JobSourceType sourceType,@RequestParam(required=false)JobPostingStatus status,@RequestParam(required=false)String company,@RequestParam(required=false)String title,@RequestParam(required=false)String location,@RequestParam(required=false)WorkplaceType workplaceType,@RequestParam(required=false)EmploymentType employmentType,@RequestParam(required=false)Instant publishedFrom,@RequestParam(required=false)Instant publishedTo,@RequestParam(required=false)Instant firstSeenFrom,@RequestParam(required=false)Instant firstSeenTo,@RequestParam(defaultValue="false")boolean includeDuplicates,@RequestParam(defaultValue="false")boolean includeArchived,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="firstSeenAt,desc")String sort){return service.list(sourceId,sourceType,status,company,title,location,workplaceType,employmentType,publishedFrom,publishedTo,firstSeenFrom,firstSeenTo,includeDuplicates,includeArchived,page,size,sort);}
    @GetMapping("/summary")public JobSummaryResponse summary(){return service.summary();}
    @GetMapping("/{id}")public JobPostingResponse get(@PathVariable UUID id){return service.get(id);}
    @PutMapping("/{id}")public JobPostingResponse update(@PathVariable UUID id,@Valid@RequestBody JobUpdateRequest request){return service.update(id,request);}
    @PostMapping("/{id}/archive")public JobPostingResponse archive(@PathVariable UUID id){return service.archive(id);}
    @PostMapping("/{id}/restore")public JobPostingResponse restore(@PathVariable UUID id){return service.restore(id);}
    @PostMapping("/{id}/mark-expired")public JobPostingResponse expire(@PathVariable UUID id){return service.markExpired(id);}
    @GetMapping("/{id}/duplicates")public List<JobPostingResponse>duplicates(@PathVariable UUID id){return service.duplicates(id);}
}
