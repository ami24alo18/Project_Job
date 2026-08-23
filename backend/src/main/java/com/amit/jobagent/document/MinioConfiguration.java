package com.amit.jobagent.document;
import com.amit.jobagent.common.config.JobAgentProperties;
import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
class MinioConfiguration {
 @Bean MinioClient minioClient(JobAgentProperties properties){var p=properties.minio();return MinioClient.builder().endpoint(p.url()).credentials(p.accessKey(),p.secretKey()).build();}
}
