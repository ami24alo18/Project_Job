package com.amit.jobagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.amit.jobagent.document.ObjectStorage;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "spring.flyway.enabled=true",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
            "job-agent.ai.enabled=false",
            "job-agent.content-generation.enabled=false",
            "job-agent.content-generation.automation-enabled=false"
        })
class PostgresJpaSchemaValidationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("job_agent")
            .withUsername("job_agent")
            .withPassword("test-password");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired Flyway flyway;
    @Autowired DataSource dataSource;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired Environment environment;

    // Keep the boot test independent of a live MinIO instance. The production bean
    // is still wired in normal application contexts; this test is about persistence.
    @MockBean ObjectStorage objectStorage;

    @Test
    void bootsJpaAgainstTheFullyMigratedPostgresSchema() throws SQLException {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("14");

        var jdbc = new JdbcTemplate(dataSource);
        List<String> appliedVersions = jdbc.queryForList(
                """
                SELECT version
                FROM job_agent.flyway_schema_history
                WHERE success = TRUE AND type = 'SQL'
                ORDER BY installed_rank
                """,
                String.class);
        assertThat(appliedVersions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14");

        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }

        assertThat(entityManagerFactory.isOpen()).isTrue();
        Set<String> managedEntities = entityManagerFactory.getMetamodel().getEntities().stream()
                .map(type -> type.getName())
                .collect(Collectors.toSet());
        assertThat(managedEntities)
                .contains(
                        "CandidateProfileVersion",
                        "JobPosting",
                        "JobEvaluation",
                        "ApplicationPackageRevision",
                        "ApplicationPackageIdempotencyAlias");
    }
}
