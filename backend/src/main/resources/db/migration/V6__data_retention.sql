-- Data retention (AGENT.md §82): finished requests older than the configured
-- period lose their personal free text and attachments; anonymised_at makes the
-- retention job idempotent. Structural and accounting data is kept.

ALTER TABLE absence_request ADD COLUMN anonymised_at TIMESTAMPTZ;
ALTER TABLE travel_request ADD COLUMN anonymised_at TIMESTAMPTZ;
ALTER TABLE time_correction_request ADD COLUMN anonymised_at TIMESTAMPTZ;

CREATE INDEX ix_absence_request_retention ON absence_request (end_date) WHERE anonymised_at IS NULL;
CREATE INDEX ix_travel_request_retention ON travel_request (end_date_time) WHERE anonymised_at IS NULL;
CREATE INDEX ix_time_correction_retention ON time_correction_request (date) WHERE anonymised_at IS NULL;
CREATE INDEX ix_notification_created ON notification (created_at);
