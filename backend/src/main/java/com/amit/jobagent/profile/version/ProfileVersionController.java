package com.amit.jobagent.profile.version;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/profile") @SecurityRequirement(name="basicAuth")
public class ProfileVersionController {
 private final ProfileSnapshotService service;public ProfileVersionController(ProfileSnapshotService service){this.service=service;}
 @PostMapping("/publish")public CandidateProfileVersionResponse publish(@Valid@RequestBody PublishProfileRequest r){return service.publish(r);}
 @GetMapping("/versions")public List<CandidateProfileVersionResponse>list(){return service.list();}
 @GetMapping("/versions/active")public CandidateProfileVersionResponse active(){return service.active();}
 @GetMapping("/versions/{number}")public CandidateProfileVersionResponse get(@PathVariable int number){return service.get(number);}
}
