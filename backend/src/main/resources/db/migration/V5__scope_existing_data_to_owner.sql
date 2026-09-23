-- Every domain table becomes owned by a user. time_block is deliberately left out: its
-- ownership is already implicit via its task relationship, and adding a column here would
-- just be unused, denormalized state that could drift from the truth.

ALTER TABLE task ADD COLUMN user_id BIGINT REFERENCES app_user (id) ON DELETE CASCADE;
UPDATE task SET user_id = 1 WHERE user_id IS NULL;
ALTER TABLE task ALTER COLUMN user_id SET NOT NULL;
CREATE INDEX idx_task_user_id ON task (user_id);

-- One event can span items across several tasks, and an item's task_id can already be null
-- after a task is deleted, so there is no reliable join path to derive ownership - this table
-- needs its own real user_id, unlike time_block.
ALTER TABLE reschedule_event ADD COLUMN user_id BIGINT REFERENCES app_user (id) ON DELETE CASCADE;
UPDATE reschedule_event SET user_id = 1 WHERE user_id IS NULL;
ALTER TABLE reschedule_event ALTER COLUMN user_id SET NOT NULL;
CREATE INDEX idx_reschedule_event_user_id ON reschedule_event (user_id);

ALTER TABLE blocked_period ADD COLUMN user_id BIGINT REFERENCES app_user (id) ON DELETE CASCADE;
UPDATE blocked_period SET user_id = 1 WHERE user_id IS NULL;
ALTER TABLE blocked_period ALTER COLUMN user_id SET NOT NULL;
CREATE INDEX idx_blocked_period_user_id ON blocked_period (user_id);

-- working_hours was keyed by day_of_week alone (one global row per weekday). Each user now
-- needs their own row per weekday, so the primary key becomes the pair. The table is recreated
-- rather than having its primary key dropped and re-added, because the original inline
-- PRIMARY KEY was never given an explicit name and H2 and PostgreSQL do not agree on what an
-- unnamed constraint is called - DROP CONSTRAINT by name would not be portable between them.
-- Constraint names are suffixed _v2 because H2, unlike PostgreSQL, scopes named constraints to
-- the whole schema rather than per-table, and the original table (with its original constraint
-- names) still exists side by side with this one until the DROP TABLE below.
CREATE TABLE working_hours_v2 (
    user_id     BIGINT    NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    day_of_week SMALLINT  NOT NULL,
    start_time  TIME      NOT NULL,
    end_time    TIME      NOT NULL,
    PRIMARY KEY (user_id, day_of_week),
    CONSTRAINT working_hours_day_of_week_iso_v2 CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT working_hours_ends_after_start_v2 CHECK (end_time > start_time)
);
INSERT INTO working_hours_v2 (user_id, day_of_week, start_time, end_time)
    SELECT 1, day_of_week, start_time, end_time FROM working_hours;
DROP TABLE working_hours;
ALTER TABLE working_hours_v2 RENAME TO working_hours;
