package com.amit.jobagent.common.config;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController @RequestMapping("/api/v1/auth") @SecurityRequirement(name="basicAuth")
public class AuthenticationController {
 @GetMapping("/check") public AuthenticationResponse check(Principal principal){return new AuthenticationResponse(principal.getName());}
 public record AuthenticationResponse(String username){}
}
