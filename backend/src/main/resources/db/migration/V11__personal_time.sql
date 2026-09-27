-- Personal time: when personal tasks may be planned, separate from working hours. Same shape as
-- working_hours, one window per user per weekday; no rows means personal tasks use working hours.
CREATE TABLE personal_hours (
    user_id     BIGINT    NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    day_of_week SMALLINT  NOT NULL,
    start_time  TIME      NOT NULL,
    end_time    TIME      NOT NULL,
    PRIMARY KEY (user_id, day_of_week),
    CONSTRAINT personal_hours_day_of_week_iso CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT personal_hours_ends_after_start CHECK (end_time > start_time)
);

-- Which hours a task is planned in. Every existing task is work, as it always was.
ALTER TABLE task ADD COLUMN profile VARCHAR(10) NOT NULL DEFAULT 'WORK';
ALTER TABLE task ADD CONSTRAINT task_profile_valid CHECK (profile IN ('WORK', 'PERSONAL'));
