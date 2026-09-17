-- Breaks become the user's own decision, the planner gains buffer time, and a fresh install
-- can tell whether its owner has set it up yet.

-- V2 seeded a 12:00-13:00 lunch on every weekday. A schedule should not impose a break nobody
-- asked for, so those exact seeded rows are removed. The match is deliberately narrow - day,
-- times and label together - so a break the user added themselves is left alone.
DELETE FROM blocked_period
WHERE day_of_week BETWEEN 1 AND 5
  AND start_time = '12:00:00'
  AND end_time = '13:00:00'
  AND label = 'Lunch';

-- Minutes kept free between scheduled tasks and around fixed blocks. 0 means none, which is
-- exactly how the planner behaved before this column existed.
ALTER TABLE scheduling_settings ADD COLUMN buffer_minutes INTEGER NOT NULL DEFAULT 0;
ALTER TABLE scheduling_settings
    ADD CONSTRAINT scheduling_settings_buffer_not_negative CHECK (buffer_minutes >= 0);

-- False until the owner saves their hours for the first time, so the app can open with setup
-- instead of an unexplained empty board.
ALTER TABLE scheduling_settings ADD COLUMN onboarded BOOLEAN NOT NULL DEFAULT FALSE;
