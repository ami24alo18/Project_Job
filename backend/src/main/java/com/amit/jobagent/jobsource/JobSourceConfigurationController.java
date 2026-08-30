package com.amit.jobagent.jobsource;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController@RequestMapping("/api/v1/job-sources")@SecurityRequirement(name="basicAuth")
public class JobSourceConfigurationController{
    private final JobSourceConfigurationService service;private final JobSpyProperties jobSpy;
    public JobSourceConfigurationController(JobSourceConfigurationService service,JobSpyProperties jobSpy){this.service=service;this.jobSpy=jobSpy;}
    @PostMapping public JobSourceConfigurationResponse create(@Valid@RequestBody JobSourceConfigurationRequest request){return service.create(request);}
    @PostMapping("/external")@ResponseStatus(HttpStatus.CREATED)public ExternalJobSourceCreatedResponse createExternal(@Valid@RequestBody ExternalJobSourceRequest request){return service.createExternal(request);}
    @PostMapping("/career-site")@ResponseStatus(HttpStatus.CREATED)public JobSourceConfigurationResponse createCareerSite(@Valid@RequestBody CareerSiteJobSourceRequest request,Principal principal){return service.createCareerSite(request,principal==null?"unknown":principal.getName());}
    @GetMapping public List<JobSourceConfigurationResponse>list(){return service.list();}
    @GetMapping("/jobspy-policy")public JobSpyPolicyResponse jobSpyPolicy(){return new JobSpyPolicyResponse(!jobSpy.allowedSites().isEmpty(),jobSpy.allowedSites());}
    @GetMapping("/{id}")public JobSourceConfigurationResponse get(@PathVariable UUID id){return service.get(id);}
    @PutMapping("/{id}")public JobSourceConfigurationResponse update(@PathVariable UUID id,@Valid@RequestBody JobSourceConfigurationRequest request){return service.update(id,request);}
    @PostMapping("/{id}/enable")public JobSourceConfigurationResponse enable(@PathVariable UUID id){return service.enable(id);}
    @PostMapping("/{id}/disable")public JobSourceConfigurationResponse disable(@PathVariable UUID id){return service.disable(id);}
    @PostMapping("/{id}/archive")public JobSourceConfigurationResponse archive(@PathVariable UUID id){return service.archive(id);}
    @PostMapping("/{id}/rotate-token")public WebhookTokenRotationResponse rotateToken(@PathVariable UUID id){return service.rotateToken(id);}
    @PostMapping("/{id}/test")public JobSourceConnectionTestResponse testConnection(@PathVariable UUID id){return service.testConnection(id);}
    @PostMapping("/{id}/recipe")public ExtractionRecipeAssociationResponse associateRecipe(@PathVariable UUID id,@Valid@RequestBody ExtractionRecipeAssociationRequest request){return service.associateRecipe(id,request);}
}
