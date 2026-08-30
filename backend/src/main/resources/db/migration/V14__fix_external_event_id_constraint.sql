ALTER TABLE job_agent.external_ingestion_event
    DROP CONSTRAINT ck_external_ingestion_event_id,
    ADD CONSTRAINT ck_external_ingestion_event_id CHECK (
        CHAR_LENGTH(event_id) BETWEEN 1 AND 300
        AND event_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]*$'
    );
