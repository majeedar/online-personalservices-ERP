-- Phase 3: shared workflow engine, delegation, notifications, absence management.

-- ------------------------------------------------------------------ workflow
CREATE TABLE workflow_definition (
    id           UUID PRIMARY KEY,
    code         VARCHAR(64)  NOT NULL,
    version      INTEGER      NOT NULL,
    description  VARCHAR(200) NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE (code, version)
);

CREATE TABLE workflow_instance (
    id                      UUID PRIMARY KEY,
    workflow_definition_id  UUID        NOT NULL REFERENCES workflow_definition (id),
    business_object_type    VARCHAR(64) NOT NULL,
    business_object_id      UUID        NOT NULL,
    requester_id            UUID        NOT NULL REFERENCES employee (id),
    current_step            INTEGER     NOT NULL,
    status                  VARCHAR(16) NOT NULL CHECK (status IN (
                                'RUNNING', 'APPROVED', 'REJECTED', 'RETURNED', 'CANCELLED')),
    created_at              TIMESTAMPTZ NOT NULL,
    completed_at            TIMESTAMPTZ,
    version                 BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX ix_workflow_instance_bo ON workflow_instance (business_object_type, business_object_id);

CREATE TABLE workflow_step (
    id                    UUID PRIMARY KEY,
    workflow_instance_id  UUID        NOT NULL REFERENCES workflow_instance (id),
    step_number           INTEGER     NOT NULL,
    step_type             VARCHAR(64) NOT NULL,
    approval_type         VARCHAR(32) NOT NULL CHECK (approval_type IN (
                              'ABSENCE', 'TRAVEL', 'TIME_CORRECTION', 'FINANCIAL', 'HR_REVIEW')),
    assigned_employee_id  UUID REFERENCES employee (id),
    assigned_role         VARCHAR(32),
    task_title            VARCHAR(200) NOT NULL,
    task_description      VARCHAR(1000),
    status                VARCHAR(16) NOT NULL CHECK (status IN (
                              'PENDING', 'ACTIVE', 'COMPLETED', 'SKIPPED', 'CANCELLED')),
    started_at            TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    UNIQUE (workflow_instance_id, step_number),
    CHECK (assigned_employee_id IS NOT NULL OR assigned_role IS NOT NULL)
);
CREATE INDEX ix_workflow_step_assignee ON workflow_step (assigned_employee_id, status);

CREATE TABLE approval_decision (
    id                UUID PRIMARY KEY,
    workflow_step_id  UUID        NOT NULL REFERENCES workflow_step (id),
    approver_id       UUID        NOT NULL REFERENCES employee (id),
    on_behalf_of_id   UUID REFERENCES employee (id),
    decision          VARCHAR(32) NOT NULL CHECK (decision IN (
                          'APPROVE', 'REJECT', 'RETURN_FOR_CORRECTION', 'FORWARD')),
    comment           VARCHAR(1000),
    decided_at        TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_approval_decision_step ON approval_decision (workflow_step_id);

-- Inbox projection of an ACTIVE workflow step (ADR-004); assignment lives on the step.
CREATE TABLE user_task (
    id                UUID PRIMARY KEY,
    workflow_step_id  UUID         NOT NULL REFERENCES workflow_step (id),
    title             VARCHAR(200) NOT NULL,
    description       VARCHAR(1000),
    due_date          DATE,
    status            VARCHAR(16)  NOT NULL CHECK (status IN ('OPEN', 'COMPLETED', 'CANCELLED')),
    created_at        TIMESTAMPTZ  NOT NULL,
    completed_at      TIMESTAMPTZ,
    version           BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_user_task_step ON user_task (workflow_step_id);
CREATE INDEX ix_user_task_open ON user_task (status) WHERE status = 'OPEN';

CREATE TABLE delegation (
    id             UUID PRIMARY KEY,
    delegator_id   UUID        NOT NULL REFERENCES employee (id),
    delegate_id    UUID        NOT NULL REFERENCES employee (id),
    approval_type  VARCHAR(32) NOT NULL CHECK (approval_type IN (
                       'ABSENCE', 'TRAVEL', 'TIME_CORRECTION', 'FINANCIAL', 'HR_REVIEW')),
    valid_from     DATE        NOT NULL,
    valid_to       DATE        NOT NULL,
    active         BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL,
    CHECK (delegator_id <> delegate_id),
    CHECK (valid_to >= valid_from)
);
CREATE INDEX ix_delegation_delegate ON delegation (delegate_id, approval_type);
CREATE INDEX ix_delegation_delegator ON delegation (delegator_id);

INSERT INTO workflow_definition (id, code, version, description) VALUES
    ('00000000-0000-0000-0000-000000000201', 'ABSENCE_APPROVAL',     1, 'Supervisor approval of an absence request'),
    ('00000000-0000-0000-0000-000000000202', 'ABSENCE_CANCELLATION', 1, 'Supervisor approval of cancelling an approved absence'),
    ('00000000-0000-0000-0000-000000000203', 'TRAVEL_APPROVAL',      1, 'Supervisor and financial approval of a business trip'),
    ('00000000-0000-0000-0000-000000000204', 'TRAVEL_EXPENSE_REVIEW', 1, 'Travel office review of an expense claim'),
    ('00000000-0000-0000-0000-000000000205', 'TIME_CORRECTION',      1, 'Supervisor / time admin approval of a time correction');

-- -------------------------------------------------------------- notification
CREATE TABLE notification (
    id                     UUID PRIMARY KEY,
    recipient_employee_id  UUID         NOT NULL REFERENCES employee (id),
    type                   VARCHAR(64)  NOT NULL,
    business_object_type   VARCHAR(64),
    business_object_id     UUID,
    subject                VARCHAR(200) NOT NULL,
    message                VARCHAR(2000) NOT NULL,
    -- delivery status of the mail channel; the row itself is the in-app channel
    status                 VARCHAR(16)  NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    created_at             TIMESTAMPTZ  NOT NULL,
    sent_at                TIMESTAMPTZ,
    read_at                TIMESTAMPTZ
);
CREATE INDEX ix_notification_recipient ON notification (recipient_employee_id, created_at DESC);

-- ------------------------------------------------------------------- absence
CREATE TABLE leave_type (
    id                    UUID PRIMARY KEY,
    code                  VARCHAR(32)  NOT NULL UNIQUE,
    name                  VARCHAR(100) NOT NULL,
    deducts_entitlement   BOOLEAN      NOT NULL,
    requires_approval     BOOLEAN      NOT NULL,
    credits_working_time  BOOLEAN      NOT NULL,
    attachment_required   BOOLEAN      NOT NULL,
    active                BOOLEAN      NOT NULL DEFAULT TRUE
);

INSERT INTO leave_type (id, code, name, deducts_entitlement, requires_approval, credits_working_time, attachment_required) VALUES
    ('00000000-0000-0000-0000-000000000301', 'ANNUAL_LEAVE',  'Annual leave',  TRUE,  TRUE,  TRUE,  FALSE),
    ('00000000-0000-0000-0000-000000000302', 'FLEX_DAY',      'Flex day (time off in lieu)', FALSE, TRUE, FALSE, FALSE),
    ('00000000-0000-0000-0000-000000000303', 'SICK_LEAVE',    'Sick leave',    FALSE, FALSE, TRUE,  FALSE),
    ('00000000-0000-0000-0000-000000000304', 'SPECIAL_LEAVE', 'Special leave', FALSE, TRUE,  TRUE,  FALSE),
    ('00000000-0000-0000-0000-000000000305', 'UNPAID_LEAVE',  'Unpaid leave',  FALSE, TRUE,  FALSE, FALSE),
    ('00000000-0000-0000-0000-000000000306', 'OTHER',         'Other absence', FALSE, TRUE,  TRUE,  FALSE);

-- remainingDays is derived (base + carry-over + additional - used - reserved), never stored.
CREATE TABLE leave_entitlement (
    id               UUID PRIMARY KEY,
    employee_id      UUID          NOT NULL REFERENCES employee (id),
    year             INTEGER       NOT NULL,
    leave_type_id    UUID          NOT NULL REFERENCES leave_type (id),
    base_days        NUMERIC(5, 2) NOT NULL CHECK (base_days >= 0),
    carry_over_days  NUMERIC(5, 2) NOT NULL DEFAULT 0 CHECK (carry_over_days >= 0),
    additional_days  NUMERIC(5, 2) NOT NULL DEFAULT 0 CHECK (additional_days >= 0),
    used_days        NUMERIC(5, 2) NOT NULL DEFAULT 0 CHECK (used_days >= 0),
    reserved_days    NUMERIC(5, 2) NOT NULL DEFAULT 0 CHECK (reserved_days >= 0),
    expiry_date      DATE,
    version          BIGINT        NOT NULL DEFAULT 0,
    UNIQUE (employee_id, year, leave_type_id)
);

CREATE TABLE absence_request (
    id                           UUID PRIMARY KEY,
    employee_id                  UUID          NOT NULL REFERENCES employee (id),
    leave_type_id                UUID          NOT NULL REFERENCES leave_type (id),
    start_date                   DATE          NOT NULL,
    end_date                     DATE          NOT NULL,
    representative_employee_id   UUID REFERENCES employee (id),
    comment                      VARCHAR(1000),
    status                       VARCHAR(16)   NOT NULL CHECK (status IN (
                                     'DRAFT', 'SUBMITTED', 'IN_APPROVAL', 'APPROVED', 'REJECTED',
                                     'CANCEL_REQUESTED', 'CANCELLED')),
    workflow_instance_id         UUID REFERENCES workflow_instance (id),
    created_at                   TIMESTAMPTZ   NOT NULL,
    submitted_at                 TIMESTAMPTZ,
    updated_at                   TIMESTAMPTZ   NOT NULL,
    version                      BIGINT        NOT NULL DEFAULT 0,
    CHECK (end_date >= start_date),
    CHECK (representative_employee_id IS NULL OR representative_employee_id <> employee_id)
);
CREATE INDEX ix_absence_request_employee ON absence_request (employee_id, start_date);
CREATE INDEX ix_absence_request_status ON absence_request (status);

CREATE TABLE absence_day (
    id                     UUID PRIMARY KEY,
    absence_request_id     UUID          NOT NULL REFERENCES absence_request (id),
    date                   DATE          NOT NULL,
    planned_minutes        INTEGER       NOT NULL CHECK (planned_minutes >= 0),
    credited_minutes       INTEGER       NOT NULL CHECK (credited_minutes >= 0),
    entitlement_deduction  NUMERIC(4, 2) NOT NULL CHECK (entitlement_deduction >= 0),
    day_kind               VARCHAR(16)   NOT NULL CHECK (day_kind IN (
                               'WORKING_DAY', 'NON_WORKING_DAY', 'HOLIDAY', 'NO_SCHEDULE')),
    UNIQUE (absence_request_id, date)
);
CREATE INDEX ix_absence_day_date ON absence_day (date);

-- ------------------------------------------------------------------ documents
-- Metadata only; file content lives in document storage, never in transaction tables (AGENT.md §14.5).
CREATE TABLE document (
    id                    UUID PRIMARY KEY,
    business_object_type  VARCHAR(64)  NOT NULL,
    business_object_id    UUID         NOT NULL,
    document_type         VARCHAR(32)  NOT NULL,
    file_name             VARCHAR(255) NOT NULL,
    content_type          VARCHAR(100) NOT NULL,
    size_bytes            BIGINT       NOT NULL CHECK (size_bytes >= 0),
    storage_reference     VARCHAR(255) NOT NULL UNIQUE,
    uploaded_by           UUID         NOT NULL REFERENCES employee (id),
    uploaded_at           TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_document_bo ON document (business_object_type, business_object_id);
