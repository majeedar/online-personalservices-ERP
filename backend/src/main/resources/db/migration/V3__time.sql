-- Phase 4: working-time recording, daily time accounts, corrections.

CREATE TABLE time_entry (
    id                     UUID PRIMARY KEY,
    employee_id            UUID        NOT NULL REFERENCES employee (id),
    timestamp              TIMESTAMPTZ NOT NULL,
    -- date in the university timezone the entry belongs to (ADR-010)
    business_date          DATE        NOT NULL,
    type                   VARCHAR(16) NOT NULL CHECK (type IN ('CLOCK_IN', 'CLOCK_OUT', 'BREAK_START', 'BREAK_END')),
    source                 VARCHAR(16) NOT NULL CHECK (source IN ('WEB', 'ADMIN', 'IMPORT', 'MOCK_TERMINAL')),
    created_at             TIMESTAMPTZ NOT NULL,
    -- entries are never deleted: a correction voids the original and adds a new entry
    voided_at              TIMESTAMPTZ,
    correction_request_id  UUID
);
CREATE INDEX ix_time_entry_employee_date ON time_entry (employee_id, business_date);

CREATE TABLE time_account_day (
    id                UUID PRIMARY KEY,
    employee_id       UUID        NOT NULL REFERENCES employee (id),
    date              DATE        NOT NULL,
    target_minutes    INTEGER     NOT NULL,
    worked_minutes    INTEGER     NOT NULL,
    break_minutes     INTEGER     NOT NULL,
    absence_minutes   INTEGER     NOT NULL,
    credited_minutes  INTEGER     NOT NULL,
    balance_minutes   INTEGER     NOT NULL,
    status            VARCHAR(24) NOT NULL CHECK (status IN ('OPEN', 'CALCULATED', 'CORRECTION_PENDING', 'CLOSED')),
    calculated_at     TIMESTAMPTZ NOT NULL,
    version           BIGINT      NOT NULL DEFAULT 0,
    UNIQUE (employee_id, date)
);

CREATE TABLE time_correction_request (
    id                      UUID PRIMARY KEY,
    employee_id             UUID          NOT NULL REFERENCES employee (id),
    date                    DATE          NOT NULL,
    operation               VARCHAR(8)    NOT NULL CHECK (operation IN ('ADD', 'MODIFY', 'DELETE')),
    original_time_entry_id  UUID REFERENCES time_entry (id),
    requested_timestamp     TIMESTAMPTZ,
    requested_type          VARCHAR(16) CHECK (requested_type IN ('CLOCK_IN', 'CLOCK_OUT', 'BREAK_START', 'BREAK_END')),
    reason                  VARCHAR(1000) NOT NULL,
    status                  VARCHAR(16)   NOT NULL CHECK (status IN ('IN_APPROVAL', 'APPROVED', 'REJECTED', 'CANCELLED')),
    workflow_instance_id    UUID REFERENCES workflow_instance (id),
    created_at              TIMESTAMPTZ   NOT NULL,
    decided_at              TIMESTAMPTZ,
    version                 BIGINT        NOT NULL DEFAULT 0,
    CHECK (operation = 'ADD' OR original_time_entry_id IS NOT NULL),
    CHECK (operation = 'DELETE' OR (requested_timestamp IS NOT NULL AND requested_type IS NOT NULL))
);
CREATE INDEX ix_time_correction_employee ON time_correction_request (employee_id, date);
