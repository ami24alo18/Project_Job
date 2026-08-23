package com.amit.jobagent.profile.answer;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/reusable-answers") @SecurityRequirement(name="basicAuth")
public class ReusableAnswerController {
 private final ReusableAnswerService service;public ReusableAnswerController(ReusableAnswerService service){this.service=service;}
 @PostMapping public ReusableAnswerResponse create(@Valid@RequestBody ReusableAnswerRequest r){return service.create(r);}
 @GetMapping public List<ReusableAnswerResponse>list(@RequestParam(required=false)ReusableAnswerCategory category,@RequestParam(required=false)AnswerSensitivity sensitivity,@RequestParam(required=false)ReusableAnswerStatus status){return service.list(category,sensitivity,status);}
 @GetMapping("/{id}")public ReusableAnswerResponse get(@PathVariable UUID id){return service.get(id);}
 @PutMapping("/{id}")public ReusableAnswerResponse update(@PathVariable UUID id,@Valid@RequestBody ReusableAnswerRequest r){return service.update(id,r);}
 @PostMapping("/{id}/verify")public ReusableAnswerResponse verify(@PathVariable UUID id){return service.verify(id);}
 @PostMapping("/{id}/archive")public ReusableAnswerResponse archive(@PathVariable UUID id){return service.archive(id);}
 @PostMapping("/{id}/restore-draft")public ReusableAnswerResponse restore(@PathVariable UUID id){return service.restore(id);}
}
