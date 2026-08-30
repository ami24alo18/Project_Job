package com.amit.jobagent.jobsource.discovery;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/job-sources")
@SecurityRequirement(name = "basicAuth")
public class CareerSiteDiscoveryController {
    private final CareerSiteDiscoveryService service;

    public CareerSiteDiscoveryController(CareerSiteDiscoveryService service) {
        this.service = service;
    }

    @PostMapping("/discover")
    public CareerSiteDiscoveryResponse discover(
            @Valid @RequestBody CareerSiteDiscoveryRequest request,
            Principal principal) {
        return service.discover(request, principal == null ? null : principal.getName());
    }
}
