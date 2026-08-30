ALTER TABLE job_agent.job_source_configuration
    DROP CONSTRAINT ck_job_source_configuration_connector_type,
    ADD CONSTRAINT ck_job_source_configuration_connector_type
        CHECK (connector_type IS NULL OR connector_type IN (
            'LEVER','GREENHOUSE','EMAIL','JSEARCH','JOBSPY','CUSTOM_WEBHOOK',
            'ORACLE_CX','WORKDAY','SMARTRECRUITERS','GENERIC_JSON_LD','CUSTOM_RECIPE'));

ALTER TABLE job_agent.external_ingestion_event
    DROP CONSTRAINT ck_external_ingestion_event_provider,
    ADD CONSTRAINT ck_external_ingestion_event_provider
        CHECK (ingestion_provider IN ('JSEARCH','JOBSPY','CUSTOM_WEBHOOK','CUSTOM_RECIPE'));

ALTER TABLE job_agent.job_posting
    DROP CONSTRAINT ck_job_posting_ingestion_provider,
    ADD CONSTRAINT ck_job_posting_ingestion_provider
        CHECK (ingestion_provider IS NULL OR ingestion_provider IN (
            'LEVER','GREENHOUSE','EMAIL','MANUAL','JSEARCH','JOBSPY','CUSTOM_WEBHOOK',
            'ORACLE_CX','WORKDAY','SMARTRECRUITERS','GENERIC_JSON_LD','CUSTOM_RECIPE'));
