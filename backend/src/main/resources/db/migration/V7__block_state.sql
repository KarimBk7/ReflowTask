-- A block is planned, done, or missed. Missed and done blocks stay as history instead of being
-- deleted: a missed part can still be marked done afterwards, and parts marked done count against
-- the estimate so only the rest is planned again.
ALTER TABLE time_block ADD COLUMN state VARCHAR(10) NOT NULL DEFAULT 'PLANNED';
ALTER TABLE time_block ADD CONSTRAINT time_block_state_valid CHECK (state IN ('PLANNED', 'DONE', 'MISSED'));
