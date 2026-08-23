package com.amit.jobagent.matching;
import io.swagger.v3.oas.annotations.security.SecurityRequirement; import jakarta.validation.Valid; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/matching/configuration") @SecurityRequirement(name="basicAuth")
public class MatchingConfigurationController{private final MatchingConfigurationService service;public MatchingConfigurationController(MatchingConfigurationService s){service=s;}@GetMapping public MatchingConfigurationResponse get(){return service.get();}@PutMapping public MatchingConfigurationResponse put(@Valid@RequestBody MatchingConfigurationRequest r){return service.put(r);}}
