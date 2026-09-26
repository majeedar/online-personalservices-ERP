-- Half-day absences (ADR-018): the first and last day of a request may cover only
-- a morning or an afternoon. Existing requests are full days.
ALTER TABLE absence_request
    ADD COLUMN start_day_part VARCHAR(16) NOT NULL DEFAULT 'FULL'
        CHECK (start_day_part IN ('FULL', 'MORNING', 'AFTERNOON')),
    ADD COLUMN end_day_part   VARCHAR(16) NOT NULL DEFAULT 'FULL'
        CHECK (end_day_part IN ('FULL', 'MORNING', 'AFTERNOON'));

ALTER TABLE absence_day
    ADD COLUMN day_part VARCHAR(16) NOT NULL DEFAULT 'FULL'
        CHECK (day_part IN ('FULL', 'MORNING', 'AFTERNOON'));
