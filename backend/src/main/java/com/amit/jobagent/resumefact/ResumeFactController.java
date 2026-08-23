package com.amit.jobagent.resumefact;
import com.amit.jobagent.common.api.PagedResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/resume-facts") @SecurityRequirement(name="basicAuth")
public class ResumeFactController {
 private final ResumeFactService service;public ResumeFactController(ResumeFactService service){this.service=service;}
 @PostMapping public ResumeFactResponse create(@Valid @RequestBody ResumeFactRequest r){return service.create(r);}
 @GetMapping public PagedResponse<ResumeFactResponse> list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)ResumeFactStatus status,@RequestParam(required=false)ResumeFactCategory category,@RequestParam(required=false)String skillTag,@RequestParam(required=false)String domainTag,@RequestParam(required=false)String search){return service.list(page,size,status,category,skillTag,domainTag,search);}
 @GetMapping("/{id}") public ResumeFactResponse get(@PathVariable UUID id){return service.get(id);}
 @PutMapping("/{id}") public ResumeFactResponse update(@PathVariable UUID id,@Valid @RequestBody ResumeFactRequest r){return service.update(id,r);}
 @PostMapping("/{id}/verify") public ResumeFactResponse verify(@PathVariable UUID id){return service.verify(id);}
 @PostMapping("/{id}/reject") public ResumeFactResponse reject(@PathVariable UUID id){return service.reject(id);}
 @PostMapping("/{id}/restore-draft") public ResumeFactResponse restore(@PathVariable UUID id){return service.restore(id);}
 @PostMapping("/{id}/archive") public ResumeFactResponse archive(@PathVariable UUID id){return service.archive(id);}
 @PostMapping("/import") public ResumeFactImportResponse importFacts(@Valid @RequestBody List<@Valid ResumeFactRequest> r){return service.importFacts(r);}
}
