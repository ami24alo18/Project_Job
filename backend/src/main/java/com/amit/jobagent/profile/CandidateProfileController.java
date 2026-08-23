package com.amit.jobagent.profile;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController @RequestMapping("/api/v1/profile") @SecurityRequirement(name="basicAuth")
public class CandidateProfileController {
    private final CandidateProfileService service;
    public CandidateProfileController(CandidateProfileService service){this.service=service;}
    @GetMapping public CandidateProfileResponse get(){return service.get();}
    @PutMapping public CandidateProfileResponse put(@Valid @RequestBody CandidateProfileRequest request){return service.upsert(request);}
}
