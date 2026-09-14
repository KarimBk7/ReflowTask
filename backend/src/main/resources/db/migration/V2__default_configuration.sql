-- Defaults for a fresh install, kept separate from the schema so changing them later
-- is a traceable migration rather than an edit to V1.

-- Mon-Fri, 09:00-18:00 (ISO day numbering: 1 = Monday).
INSERT INTO working_hours (day_of_week, start_time, end_time) VALUES
    (1, '09:00:00', '18:00:00'),
    (2, '09:00:00', '18:00:00'),
    (3, '09:00:00', '18:00:00'),
    (4, '09:00:00', '18:00:00'),
    (5, '09:00:00', '18:00:00');

-- Lunch, every working day.
INSERT INTO blocked_period (day_of_week, start_time, end_time, label) VALUES
    (1, '12:00:00', '13:00:00', 'Lunch'),
    (2, '12:00:00', '13:00:00', 'Lunch'),
    (3, '12:00:00', '13:00:00', 'Lunch'),
    (4, '12:00:00', '13:00:00', 'Lunch'),
    (5, '12:00:00', '13:00:00', 'Lunch');

-- horizon_days: how far ahead the scheduler will place work.
-- min_chunk_minutes: the smallest piece a split task may be broken into, so a long
-- task does not shatter into useless 5-minute fragments.
INSERT INTO scheduling_settings (id, horizon_days, min_chunk_minutes) VALUES (1, 14, 30);
