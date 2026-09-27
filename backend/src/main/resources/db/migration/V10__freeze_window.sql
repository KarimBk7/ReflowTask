-- Minutes ahead in which planned blocks are left where they are by a replan, so a new urgent task
-- does not reshuffle what is about to start. 0 turns it off, which is how replanning behaved before.
ALTER TABLE scheduling_settings ADD COLUMN freeze_minutes INTEGER NOT NULL DEFAULT 0;
ALTER TABLE scheduling_settings
    ADD CONSTRAINT scheduling_settings_freeze_not_negative CHECK (freeze_minutes >= 0);
