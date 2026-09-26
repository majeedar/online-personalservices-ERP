-- Phase 1/2: organisation, employee master data, roles, approval relations,
-- work schedules and holiday calendar.
-- All internal IDs are UUIDs; system timestamps are timestamptz (UTC).

CREATE TABLE organisation_unit (
    id           UUID PRIMARY KEY,
    external_id  VARCHAR(64)  NOT NULL UNIQUE,
    code         VARCHAR(32)  NOT NULL UNIQUE,
    name         VARCHAR(200) NOT NULL,
    type         VARCHAR(32)  NOT NULL CHECK (type IN (
                     'UNIVERSITY', 'CENTRAL_ADMINISTRATION', 'FACULTY', 'INSTITUTE',
                     'DEPARTMENT', 'UNIT', 'PROJECT_UNIT')),
    parent_id    UUID REFERENCES organisation_unit (id),
    cost_centre  VARCHAR(32),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    version      BIGINT       NOT NULL DEFAULT 0,
    synced_at    TIMESTAMPTZ
);
CREATE INDEX ix_organisation_unit_parent ON organisation_unit (parent_id);

CREATE TABLE employee (
    id                    UUID PRIMARY KEY,
    external_employee_id  VARCHAR(64)  NOT NULL UNIQUE,
    personnel_number      VARCHAR(32)  NOT NULL UNIQUE,
    first_name            VARCHAR(100) NOT NULL,
    last_name             VARCHAR(100) NOT NULL,
    email                 VARCHAR(200) NOT NULL,
    username              VARCHAR(64)  NOT NULL UNIQUE,
    organisation_unit_id  UUID         NOT NULL REFERENCES organisation_unit (id),
    primary_employment_id UUID,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    version               BIGINT       NOT NULL DEFAULT 0,
    synced_at             TIMESTAMPTZ
);
CREATE INDEX ix_employee_org_unit ON employee (organisation_unit_id);

CREATE TABLE work_schedule (
    id                     UUID PRIMARY KEY,
    employee_id            UUID    NOT NULL REFERENCES employee (id),
    valid_from             DATE    NOT NULL,
    valid_to               DATE,
    weekly_target_minutes  INTEGER NOT NULL CHECK (weekly_target_minutes >= 0),
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE INDEX ix_work_schedule_employee ON work_schedule (employee_id, valid_from);

CREATE TABLE work_schedule_day (
    id                UUID PRIMARY KEY,
    work_schedule_id  UUID        NOT NULL REFERENCES work_schedule (id),
    weekday           VARCHAR(10) NOT NULL CHECK (weekday IN (
                          'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    target_minutes    INTEGER     NOT NULL CHECK (target_minutes >= 0),
    working_day       BOOLEAN     NOT NULL,
    UNIQUE (work_schedule_id, weekday)
);

CREATE TABLE employment (
    id                      UUID PRIMARY KEY,
    employee_id             UUID          NOT NULL REFERENCES employee (id),
    external_employment_id  VARCHAR(64)   NOT NULL UNIQUE,
    start_date              DATE          NOT NULL,
    end_date                DATE,
    employment_type         VARCHAR(32)   NOT NULL CHECK (employment_type IN (
                                'ACADEMIC', 'ADMINISTRATIVE', 'TECHNICAL', 'STUDENT_ASSISTANT', 'OTHER')),
    weekly_hours            NUMERIC(5, 2) NOT NULL CHECK (weekly_hours >= 0),
    full_time_equivalent    NUMERIC(4, 3) NOT NULL CHECK (full_time_equivalent BETWEEN 0 AND 1),
    work_schedule_id        UUID REFERENCES work_schedule (id),
    status                  VARCHAR(16)   NOT NULL CHECK (status IN ('ACTIVE', 'FUTURE', 'ENDED', 'SUSPENDED')),
    version                 BIGINT        NOT NULL DEFAULT 0,
    CHECK (end_date IS NULL OR end_date >= start_date)
);
CREATE INDEX ix_employment_employee ON employment (employee_id);

ALTER TABLE employee
    ADD CONSTRAINT fk_employee_primary_employment
    FOREIGN KEY (primary_employment_id) REFERENCES employment (id);

CREATE TABLE role (
    id    UUID PRIMARY KEY,
    name  VARCHAR(32) NOT NULL UNIQUE
);

CREATE TABLE user_role (
    id           UUID PRIMARY KEY,
    employee_id  UUID NOT NULL REFERENCES employee (id),
    role_id      UUID NOT NULL REFERENCES role (id),
    valid_from   DATE NOT NULL,
    valid_to     DATE,
    UNIQUE (employee_id, role_id, valid_from),
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE INDEX ix_user_role_employee ON user_role (employee_id);

CREATE TABLE approval_relation (
    id             UUID PRIMARY KEY,
    employee_id    UUID        NOT NULL REFERENCES employee (id),
    approver_id    UUID        NOT NULL REFERENCES employee (id),
    approval_type  VARCHAR(32) NOT NULL CHECK (approval_type IN (
                       'ABSENCE', 'TRAVEL', 'TIME_CORRECTION', 'FINANCIAL', 'HR_REVIEW')),
    priority       INTEGER     NOT NULL DEFAULT 1,
    valid_from     DATE        NOT NULL,
    valid_to       DATE,
    CHECK (employee_id <> approver_id),
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE INDEX ix_approval_relation_employee ON approval_relation (employee_id, approval_type);
CREATE INDEX ix_approval_relation_approver ON approval_relation (approver_id, approval_type);

CREATE TABLE holiday (
    id           UUID PRIMARY KEY,
    date         DATE         NOT NULL,
    name         VARCHAR(100) NOT NULL,
    region_code  VARCHAR(16)  NOT NULL,
    UNIQUE (date, region_code)
);

-- Audit log: append-only (ADR-008). The trigger below rejects UPDATE, DELETE
-- and TRUNCATE for every database user, including the application's own.
CREATE TABLE audit_log (
    id                 UUID PRIMARY KEY,
    actor_employee_id  UUID,
    actor_username     VARCHAR(64),
    action             VARCHAR(64)  NOT NULL,
    entity_type        VARCHAR(64)  NOT NULL,
    entity_id          VARCHAR(64),
    old_value_json     JSONB,
    new_value_json     JSONB,
    timestamp          TIMESTAMPTZ  NOT NULL,
    correlation_id     VARCHAR(64)
);
CREATE INDEX ix_audit_log_entity ON audit_log (entity_type, entity_id);
CREATE INDEX ix_audit_log_timestamp ON audit_log (timestamp DESC);
CREATE INDEX ix_audit_log_actor ON audit_log (actor_employee_id);

CREATE FUNCTION audit_log_is_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only (% rejected)', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_is_append_only();

CREATE TRIGGER trg_audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_log_is_append_only();

-- Spring Modulith event publication registry = transactional outbox (ADR-006).
CREATE TABLE event_publication (
    id                UUID PRIMARY KEY,
    listener_id       TEXT        NOT NULL,
    event_type        TEXT        NOT NULL,
    serialized_event  TEXT        NOT NULL,
    publication_date  TIMESTAMPTZ NOT NULL,
    completion_date   TIMESTAMPTZ
);
CREATE INDEX ix_event_publication_incomplete ON event_publication (completion_date)
    WHERE completion_date IS NULL;

-- Reference data (not demo data): the fixed role catalogue.
INSERT INTO role (id, name) VALUES
    ('00000000-0000-0000-0000-000000000101', 'EMPLOYEE'),
    ('00000000-0000-0000-0000-000000000102', 'SUPERVISOR'),
    ('00000000-0000-0000-0000-000000000103', 'HR_ADMIN'),
    ('00000000-0000-0000-0000-000000000104', 'TRAVEL_OFFICE'),
    ('00000000-0000-0000-0000-000000000105', 'FINANCIAL_APPROVER'),
    ('00000000-0000-0000-0000-000000000106', 'TIME_ADMIN'),
    ('00000000-0000-0000-0000-000000000107', 'ERP_ADMIN'),
    ('00000000-0000-0000-0000-000000000108', 'SUPPORT'),
    ('00000000-0000-0000-0000-000000000109', 'AUDITOR');
