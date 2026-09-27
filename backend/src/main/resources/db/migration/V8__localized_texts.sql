-- Server texts in the reader's language (ADR-020). Notifications and tasks store the
-- English template plus its values as JSON and are rendered when read. The existing
-- English columns stay as the fallback (older rows, logs, exports).
ALTER TABLE notification
    ADD COLUMN subject_text JSONB,
    ADD COLUMN message_text JSONB;

ALTER TABLE workflow_step
    ADD COLUMN task_title_text       JSONB,
    ADD COLUMN task_description_text JSONB;

ALTER TABLE user_task
    ADD COLUMN title_text       JSONB,
    ADD COLUMN description_text JSONB;

-- Interface and e-mail language chosen by the employee; NULL = not chosen yet (English mail).
ALTER TABLE employee
    ADD COLUMN preferred_language VARCHAR(2) CHECK (preferred_language IN ('en', 'de'));
