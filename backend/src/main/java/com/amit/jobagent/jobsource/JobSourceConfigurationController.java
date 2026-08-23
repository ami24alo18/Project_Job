package com.amit.jobagent.jobsource;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController@RequestMapping("/api/v1/job-sources")@SecurityRequirement(name="basicAuth")
public class JobSourceConfigurationController{
    private final JobSourceConfigurationService service;public JobSourceConfigurationController(JobSourceConfigurationService service){this.service=service;}
    @PostMapping public JobSourceConfigurationResponse create(@Valid@RequestBody JobSourceConfigurationRequest request){return service.create(request);}
    @GetMapping public List<JobSourceConfigurationResponse>list(){return service.list();}
    @GetMapping("/{id}")public JobSourceConfigurationResponse get(@PathVariable UUID id){return service.get(id);}
    @PutMapping("/{id}")public JobSourceConfigurationResponse update(@PathVariable UUID id,@Valid@RequestBody JobSourceConfigurationRequest request){return service.update(id,request);}
    @PostMapping("/{id}/enable")public JobSourceConfigurationResponse enable(@PathVariable UUID id){return service.enable(id);}
    @PostMapping("/{id}/disable")public JobSourceConfigurationResponse disable(@PathVariable UUID id){return service.disable(id);}
    @PostMapping("/{id}/archive")public JobSourceConfigurationResponse archive(@PathVariable UUID id){return service.archive(id);}
}
