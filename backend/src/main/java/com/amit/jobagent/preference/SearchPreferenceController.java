package com.amit.jobagent.preference;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController @RequestMapping("/api/v1/profile/preferences") @SecurityRequirement(name="basicAuth")
public class SearchPreferenceController {
 private final SearchPreferenceService service;public SearchPreferenceController(SearchPreferenceService service){this.service=service;}
 @GetMapping public SearchPreferenceResponse get(){return service.get();}
 @PutMapping public SearchPreferenceResponse put(@Valid @RequestBody SearchPreferenceRequest request){return service.upsert(request);}
}
