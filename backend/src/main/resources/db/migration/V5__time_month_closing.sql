-- Monthly closing of time accounts. A closed month's days are frozen
-- (time_account_day.status = 'CLOSED'); corrections and backdated bookings for it are
-- rejected until it is reopened. History is kept: reopening marks the row REOPENED,
-- closing again adds a new row.

CREATE TABLE time_month_closing (
    id               UUID PRIMARY KEY,
    year_month       VARCHAR(7)  NOT NULL CHECK (year_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    status           VARCHAR(16) NOT NULL CHECK (status IN ('CLOSED', 'REOPENED')),
    closed_at        TIMESTAMPTZ NOT NULL,
    closed_by        VARCHAR(64) NOT NULL,
    employees        INTEGER     NOT NULL,
    days             INTEGER     NOT NULL,
    reopened_at      TIMESTAMPTZ,
    reopened_by      VARCHAR(64),
    reopen_reason    VARCHAR(1000),
    version          BIGINT      NOT NULL DEFAULT 0
);
-- At most one active closing per month.
CREATE UNIQUE INDEX ux_time_month_closing_active ON time_month_closing (year_month) WHERE status = 'CLOSED';
