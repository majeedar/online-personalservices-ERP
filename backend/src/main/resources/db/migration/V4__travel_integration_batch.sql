-- Phase 5–7: business travel, integration monitoring, external-export outbox, batch history.

-- -------------------------------------------------------------------- travel
CREATE TABLE funding_source (
    id           UUID PRIMARY KEY,
    cost_centre  VARCHAR(32)  NOT NULL,
    project_code VARCHAR(32),
    fund_code    VARCHAR(32)  NOT NULL,
    description  VARCHAR(200) NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE NULLS NOT DISTINCT (cost_centre, project_code, fund_code)
);

CREATE TABLE travel_request (
    id                           UUID PRIMARY KEY,
    employee_id                  UUID          NOT NULL REFERENCES employee (id),
    purpose                      VARCHAR(500)  NOT NULL,
    destination_city             VARCHAR(100)  NOT NULL,
    destination_country          VARCHAR(2)    NOT NULL,
    start_date_time              TIMESTAMPTZ   NOT NULL,
    end_date_time                TIMESTAMPTZ   NOT NULL,
    transport_mode               VARCHAR(20)   NOT NULL CHECK (transport_mode IN (
                                     'TRAIN', 'PUBLIC_TRANSPORT', 'CAR', 'FLIGHT', 'BICYCLE', 'OTHER')),
    estimated_cost               NUMERIC(12, 2) NOT NULL CHECK (estimated_cost >= 0),
    currency                     VARCHAR(3)    NOT NULL,
    cost_centre                  VARCHAR(32)   NOT NULL,
    project_code                 VARCHAR(32),
    comment                      VARCHAR(1000),
    status                       VARCHAR(24)   NOT NULL CHECK (status IN (
                                     'DRAFT', 'IN_APPROVAL', 'AUTHORIZED', 'REJECTED', 'COMPLETED',
                                     'EXPENSES_SUBMITTED', 'SETTLED', 'CANCELLED')),
    workflow_instance_id         UUID REFERENCES workflow_instance (id),
    external_travel_reference    VARCHAR(64),
    settled_amount               NUMERIC(12, 2),
    settlement_reference         VARCHAR(64),
    finance_posting_reference    VARCHAR(64),
    created_at                   TIMESTAMPTZ   NOT NULL,
    submitted_at                 TIMESTAMPTZ,
    updated_at                   TIMESTAMPTZ   NOT NULL,
    version                      BIGINT        NOT NULL DEFAULT 0,
    CHECK (end_date_time > start_date_time)
);
CREATE INDEX ix_travel_request_employee ON travel_request (employee_id, start_date_time);
CREATE INDEX ix_travel_request_status ON travel_request (status);

CREATE TABLE travel_funding (
    id                 UUID PRIMARY KEY,
    travel_request_id  UUID          NOT NULL REFERENCES travel_request (id),
    funding_source_id  UUID          NOT NULL REFERENCES funding_source (id),
    percentage         NUMERIC(5, 2) CHECK (percentage IS NULL OR percentage BETWEEN 0 AND 100),
    amount             NUMERIC(12, 2) CHECK (amount IS NULL OR amount >= 0)
);
CREATE INDEX ix_travel_funding_request ON travel_funding (travel_request_id);

CREATE TABLE travel_expense (
    id                   UUID PRIMARY KEY,
    travel_request_id    UUID          NOT NULL REFERENCES travel_request (id),
    expense_type         VARCHAR(20)   NOT NULL CHECK (expense_type IN (
                             'TRAIN', 'FLIGHT', 'HOTEL', 'TAXI', 'LOCAL_TRANSPORT', 'MILEAGE', 'MEALS',
                             'CONFERENCE_FEE', 'OTHER')),
    expense_date         DATE          NOT NULL,
    amount               NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency             VARCHAR(3)    NOT NULL,
    description          VARCHAR(500),
    receipt_document_id  UUID REFERENCES document (id),
    status               VARCHAR(16)   NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'ACCEPTED', 'REJECTED'))
);
CREATE INDEX ix_travel_expense_request ON travel_expense (travel_request_id);

-- ---------------------------------------------------- integration monitoring
CREATE TABLE integration_run (
    id               UUID PRIMARY KEY,
    interface_name   VARCHAR(64) NOT NULL,
    trigger          VARCHAR(16) NOT NULL CHECK (trigger IN ('SCHEDULED', 'MANUAL', 'EVENT', 'RETRY')),
    started_at       TIMESTAMPTZ NOT NULL,
    finished_at      TIMESTAMPTZ,
    status           VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED')),
    records_read     INTEGER     NOT NULL DEFAULT 0,
    records_written  INTEGER     NOT NULL DEFAULT 0,
    records_failed   INTEGER     NOT NULL DEFAULT 0,
    correlation_id   VARCHAR(64)
);
CREATE INDEX ix_integration_run_started ON integration_run (started_at DESC);

CREATE TABLE integration_error (
    id                  UUID PRIMARY KEY,
    integration_run_id  UUID          NOT NULL REFERENCES integration_run (id),
    external_reference  VARCHAR(128),
    error_code          VARCHAR(64)   NOT NULL,
    error_message       VARCHAR(1000) NOT NULL,
    retry_count         INTEGER       NOT NULL DEFAULT 0,
    outbox_event_id     UUID,
    created_at          TIMESTAMPTZ   NOT NULL,
    resolved            BOOLEAN       NOT NULL DEFAULT FALSE,
    resolved_at         TIMESTAMPTZ
);
CREATE INDEX ix_integration_error_open ON integration_error (resolved, created_at DESC);

-- Explicit outbox for external exports (ADR-006, AGENT.md §50). Written in the
-- business transaction; delivered by the outbox processor with retries.
CREATE TABLE outbox_event (
    id               UUID PRIMARY KEY,
    event_type       VARCHAR(64)  NOT NULL,
    aggregate_type   VARCHAR(64)  NOT NULL,
    aggregate_id     UUID         NOT NULL,
    idempotency_key  VARCHAR(128) NOT NULL UNIQUE,
    payload          JSONB        NOT NULL,
    status           VARCHAR(16)  NOT NULL CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
    attempts         INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ  NOT NULL,
    last_error       VARCHAR(1000),
    created_at       TIMESTAMPTZ  NOT NULL,
    processed_at     TIMESTAMPTZ
);
CREATE INDEX ix_outbox_due ON outbox_event (status, next_attempt_at);

-- -------------------------------------------------------------- batch jobs
CREATE TABLE batch_job_run (
    id                  UUID PRIMARY KEY,
    job_name            VARCHAR(64) NOT NULL,
    trigger             VARCHAR(16) NOT NULL CHECK (trigger IN ('SCHEDULED', 'MANUAL')),
    started_at          TIMESTAMPTZ NOT NULL,
    finished_at         TIMESTAMPTZ,
    status              VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED')),
    processed_records   INTEGER     NOT NULL DEFAULT 0,
    successful_records  INTEGER     NOT NULL DEFAULT 0,
    failed_records      INTEGER     NOT NULL DEFAULT 0,
    started_by          VARCHAR(64),
    correlation_id      VARCHAR(64)
);
CREATE INDEX ix_batch_job_run_started ON batch_job_run (started_at DESC);
-- At most one running instance per job, enforced by the database (restartable, no duplicate processing).
CREATE UNIQUE INDEX ux_batch_job_running ON batch_job_run (job_name) WHERE status = 'RUNNING';

CREATE TABLE batch_job_error (
    id                UUID PRIMARY KEY,
    batch_job_run_id  UUID          NOT NULL REFERENCES batch_job_run (id),
    record_reference  VARCHAR(128),
    error_code        VARCHAR(64)   NOT NULL,
    error_message     VARCHAR(1000) NOT NULL,
    retry_count       INTEGER       NOT NULL DEFAULT 0
);
CREATE INDEX ix_batch_job_error_run ON batch_job_error (batch_job_run_id);
