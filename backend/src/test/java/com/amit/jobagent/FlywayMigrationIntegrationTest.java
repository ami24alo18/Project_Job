package com.amit.jobagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker=true)
class FlywayMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("job_agent").withUsername("job_agent").withPassword("test-password");

    private Flyway flyway(String target){
        var config=Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())
                .schemas("job_agent").defaultSchema("job_agent").cleanDisabled(false);
        if(target!=null)config.target(target);return config.load();
    }

    @Test void migratesCleanDatabaseThroughUnifiedJobSourceFoundation()throws Exception{
        var f=flyway(null);f.clean();var result=f.migrate();assertThat(result.targetSchemaVersion).isEqualTo("15");
        try(var c=POSTGRES.createConnection("");var s=c.prepareStatement("SELECT COUNT(*) FROM job_agent.job_posting")){
            assertThat(s.executeQuery().next()).isTrue();
        }
    }

    @Test void migratesFromPhaseTwoBaseline()throws Exception{
        var baseline=flyway("2");baseline.clean();assertThat(baseline.migrate().targetSchemaVersion).isEqualTo("2");
        var current=flyway(null);assertThat(current.migrate().targetSchemaVersion).isEqualTo("15");
        assertThat(current.info().current().getVersion().getVersion()).isEqualTo("15");
    }

    @Test void createsPhaseFiveApplicationProvenanceAndArtifactSchema()throws Exception{
        var f=flyway(null);f.clean();f.migrate();
        try(var c=POSTGRES.createConnection("");
            var s=c.prepareStatement("""
                    SELECT to_regclass('job_agent.application_package'),
                           to_regclass('job_agent.application_package_revision'),
                           to_regclass('job_agent.generated_content'),
                           to_regclass('job_agent.generated_claim'),
                           to_regclass('job_agent.generated_claim_source'),
                           to_regclass('job_agent.application_question_draft'),
                           to_regclass('job_agent.document_artifact'),
                           to_regclass('job_agent.application_package_idempotency_alias')
                    """)){
            var result=s.executeQuery();assertThat(result.next()).isTrue();
            for(int column=1;column<=8;column++)assertThat(result.getString(column)).isNotNull();
        }
        try(var c=POSTGRES.createConnection("");
            var s=c.prepareStatement("""
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema='job_agent' AND table_name='llm_execution'
                      AND column_name IN ('operation_type','input_checksum','output_checksum','cache_hit',
                                          'source_job_checksum','source_profile_checksum','source_evaluation_checksum')
                    """)){
            var result=s.executeQuery();assertThat(result.next()).isTrue();assertThat(result.getInt(1)).isEqualTo(7);
        }
        try(var c=POSTGRES.createConnection("");
            var s=c.prepareStatement("""
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema='job_agent' AND table_name='application_package_revision'
                      AND column_name='generation_settings_checksum' AND is_nullable='NO'
                    """)){
            var result=s.executeQuery();assertThat(result.next()).isTrue();assertThat(result.getInt(1)).isEqualTo(1);
        }
    }

    @Test void enforcesPhaseThreeIdentityAndActiveRunConstraints()throws Exception{
        var f=flyway(null);f.clean();f.migrate();var sourceId=UUID.randomUUID();
        try(var c=POSTGRES.createConnection("")){
            insertSource(c,sourceId,"fictional-board");
            assertThatThrownBy(()->insertSource(c,UUID.randomUUID(),"FICTIONAL-BOARD")).isInstanceOf(SQLException.class);
            try(var run=c.prepareStatement("INSERT INTO job_agent.job_source_run(id,source_id,trigger_type,status,created_at) VALUES (?,?, 'MANUAL','QUEUED',CURRENT_TIMESTAMP)")){
                run.setObject(1,UUID.randomUUID());run.setObject(2,sourceId);run.executeUpdate();
            }
            assertThatThrownBy(()->{try(var run=c.prepareStatement("INSERT INTO job_agent.job_source_run(id,source_id,trigger_type,status,created_at) VALUES (?,?, 'RETRY','RUNNING',CURRENT_TIMESTAMP)")){run.setObject(1,UUID.randomUUID());run.setObject(2,sourceId);run.executeUpdate();}}).isInstanceOf(SQLException.class);
        }
    }

    @Test void createsUnifiedSourceEventRuleAndProvenanceSchema()throws Exception{
        var f=flyway(null);f.clean();f.migrate();
        try(var c=POSTGRES.createConnection("");
            var tables=c.prepareStatement("""
                    SELECT to_regclass('job_agent.job_source_search_rule'),
                           to_regclass('job_agent.external_ingestion_event'),
                           to_regclass('job_agent.external_ingestion_event_result')
                    """)){
            var result=tables.executeQuery();assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isNotNull();assertThat(result.getString(2)).isNotNull();assertThat(result.getString(3)).isNotNull();
        }
        try(var c=POSTGRES.createConnection("");
            var columns=c.prepareStatement("""
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema='job_agent' AND (
                        (table_name='job_source_configuration' AND column_name IN ('connector_type','career_site_url','canonical_host','support_status','webhook_token_hash')) OR
                        (table_name='job_source_run' AND column_name IN ('coverage','search_rule_id','external_event_id')) OR
                        (table_name='job_posting' AND column_name IN ('ingestion_provider','origin_publisher','discovery_query','external_event_id'))
                    )
                    """)){
            var result=columns.executeQuery();assertThat(result.next()).isTrue();assertThat(result.getInt(1)).isEqualTo(12);
        }
    }

    @Test void acceptsBoundedExternalEventIdentifiersOnPostgres()throws Exception{
        var f=flyway(null);f.clean();f.migrate();var sourceId=UUID.randomUUID();
        try(var c=POSTGRES.createConnection("")){
            insertSource(c,sourceId,"external-event-constraint-board");
            try(var statement=c.prepareStatement("INSERT INTO job_agent.external_ingestion_event(id,source_id,event_id,ingestion_provider,fetched_at,payload_checksum,status,created_at) VALUES (?,?,?,'JOBSPY',CURRENT_TIMESTAMP,?,'PROCESSING',CURRENT_TIMESTAMP)")){
                statement.setObject(1,UUID.randomUUID());statement.setObject(2,sourceId);
                statement.setString(3,"n"+"a".repeat(299));statement.setString(4,"c".repeat(64));
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
        }
    }

    @Test void snapshotEvidenceHasDedicatedStorageWithoutWeakeningLegacyFactIntegrity()throws Exception{
        var f=flyway(null);f.clean();f.migrate();
        try(var c=POSTGRES.createConnection("");
            var s=c.prepareStatement("""
                    SELECT
                      COUNT(*) FILTER (WHERE column_name = 'candidate_snapshot_evidence_id'),
                      (SELECT COUNT(*)
                         FROM pg_constraint constraint_definition
                        WHERE constraint_definition.conrelid = 'job_agent.generated_claim_source'::regclass
                          AND constraint_definition.contype = 'f'
                          AND pg_get_constraintdef(constraint_definition.oid) LIKE '%candidate_fact_id%')
                    FROM information_schema.columns
                    WHERE table_schema = 'job_agent'
                      AND table_name = 'generated_claim_source'
                    """)){
            var result=s.executeQuery();assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
            assertThat(result.getInt(2)).isEqualTo(1);
        }
    }

    @Test void enforcesJobEmailAndSourceForeignKeyConstraints()throws Exception{
        var f=flyway(null);f.clean();f.migrate();var sourceId=UUID.randomUUID();
        try(var c=POSTGRES.createConnection("")){
            insertSource(c,sourceId,"constraint-board");
            insertJob(c,UUID.randomUUID(),sourceId,"provider-job-1");
            assertThatThrownBy(()->insertJob(c,UUID.randomUUID(),sourceId,"provider-job-1"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(()->insertJob(c,UUID.randomUUID(),UUID.randomUUID(),"missing-source-job"))
                    .isInstanceOf(SQLException.class);
            insertEmailEvent(c,UUID.randomUUID(),sourceId,"fictional-message-1");
            assertThatThrownBy(()->insertEmailEvent(c,UUID.randomUUID(),sourceId,"fictional-message-1"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private static void insertSource(java.sql.Connection c,UUID id,String identifier)throws SQLException{
        try(var statement=c.prepareStatement("INSERT INTO job_agent.job_source_configuration(id,display_name,source_type,provider_identifier,region,enabled,page_size,maximum_pages_per_run,missing_run_threshold,consecutive_failure_count,record_version,created_at,updated_at) VALUES (?,?,'GREENHOUSE',?,'DEFAULT',TRUE,50,5,2,0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")){
            statement.setObject(1,id);statement.setString(2,"Fictional Board");statement.setString(3,identifier);statement.executeUpdate();
        }
    }

    private static void insertJob(java.sql.Connection c,UUID id,UUID sourceId,String externalId)throws SQLException{
        try(var statement=c.prepareStatement("INSERT INTO job_agent.job_posting(id,source_id,source_type,external_id,company,title,workplace_type,employment_type,description_truncated,salary_interval,first_seen_at,last_seen_at,missing_successful_run_count,fingerprint,content_hash,source_content_hash,status,manually_edited,source_update_available,record_version,created_at,updated_at) VALUES (?,?, 'GREENHOUSE',?,'Example Company','Backend Engineer','UNSPECIFIED','UNSPECIFIED',FALSE,'UNSPECIFIED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,?,?,?,'READY_FOR_EVALUATION',FALSE,FALSE,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")){
            statement.setObject(1,id);statement.setObject(2,sourceId);statement.setString(3,externalId);
            statement.setString(4,"a".repeat(64));statement.setString(5,"b".repeat(64));statement.setString(6,"b".repeat(64));statement.executeUpdate();
        }
    }

    private static void insertEmailEvent(java.sql.Connection c,UUID id,UUID sourceId,String messageId)throws SQLException{
        try(var statement=c.prepareStatement("INSERT INTO job_agent.email_ingestion_event(id,message_id,provider,source_id,received_at,status,job_count,created_count,updated_count,unchanged_count,duplicate_count,failed_count,created_at) VALUES (?,?,'GMAIL',?,CURRENT_TIMESTAMP,'SUCCEEDED',0,0,0,0,0,0,CURRENT_TIMESTAMP)")){
            statement.setObject(1,id);statement.setString(2,messageId);statement.setObject(3,sourceId);statement.executeUpdate();
        }
    }
}
