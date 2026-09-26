-- A repeating task is one open occurrence at a time. When it is done, the next occurrence is
-- created one interval later and the rule moves on to it, so the finished one stays plain history.
ALTER TABLE task ADD COLUMN recurrence VARCHAR(10);
ALTER TABLE task ADD CONSTRAINT task_recurrence_valid
    CHECK (recurrence IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY'));
-- Each occurrence is due one interval after the last, so a rule needs a deadline to count from.
ALTER TABLE task ADD CONSTRAINT task_recurrence_needs_deadline
    CHECK (recurrence IS NULL OR deadline IS NOT NULL);

-- Work is not planned before this. Set on each next occurrence to where the previous one was due,
-- so next week's report is not done this week.
ALTER TABLE task ADD COLUMN not_before TIMESTAMP;
