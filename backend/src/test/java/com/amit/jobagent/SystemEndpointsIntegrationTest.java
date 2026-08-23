package com.amit.jobagent;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemEndpointsIntegrationTest {
    @Autowired MockMvc mockMvc;

    @Test
    void applicationContextStarts() {}

    @Test
    void systemHealthIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("job-agent-backend"))
                .andExpect(jsonPath("$.timestamp", matchesPattern(".+Z")));
    }

    @Test
    void actuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void n8nPingAcceptsCorrectSecret() throws Exception {
        mockMvc.perform(post("/api/v1/system/n8n/ping").header("X-N8N-WEBHOOK-SECRET", "test-n8n-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.source").value("n8n"));
    }

    @Test
    void n8nPingRejectsMissingSecret() throws Exception {
        mockMvc.perform(post("/api/v1/system/n8n/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Required header missing"));
    }

    @Test
    void n8nPingRejectsIncorrectSecret() throws Exception {
        mockMvc.perform(post("/api/v1/system/n8n/ping").header("X-N8N-WEBHOOK-SECRET", "wrong"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Webhook authentication failed"));
    }

    @Test
    void unknownEndpointIsDeniedByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/definitely-unknown"))
                .andExpect(status().isForbidden());
    }

    @Test
    void jobEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsConfiguredFrontend() throws Exception {
        mockMvc.perform(options("/api/v1/system/health")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }
}
