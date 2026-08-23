package com.amit.jobagent.document;
import static org.assertj.core.api.Assertions.assertThat;
import com.amit.jobagent.common.config.JobAgentProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
@Testcontainers(disabledWithoutDocker=true)
class MinioObjectStorageIntegrationTest {
 @Container static final GenericContainer<?> MINIO=new GenericContainer<>(DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z")).withEnv("MINIO_ROOT_USER","minioadmin").withEnv("MINIO_ROOT_PASSWORD","minio-test-password").withCommand("server","/data").withExposedPorts(9000);
 @Test void storesAndLoadsPrivateObject(){var endpoint="http://"+MINIO.getHost()+":"+MINIO.getMappedPort(9000);var client=MinioClient.builder().endpoint(endpoint).credentials("minioadmin","minio-test-password").build();var props=new JobAgentProperties("http://localhost",new JobAgentProperties.N8n("x"),new JobAgentProperties.Minio(endpoint,"minioadmin","minio-test-password","documents"),new JobAgentProperties.Security("u","p"),new JobAgentProperties.Documents(100));var storage=new MinioObjectStorage(client,props);storage.store("profiles/test/resume.pdf","private".getBytes(),"application/pdf");assertThat(storage.load("profiles/test/resume.pdf")).isEqualTo("private".getBytes());}
}
