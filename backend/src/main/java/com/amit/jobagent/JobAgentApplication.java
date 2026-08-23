package com.amit.jobagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JobAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobAgentApplication.class, args);
    }
}
