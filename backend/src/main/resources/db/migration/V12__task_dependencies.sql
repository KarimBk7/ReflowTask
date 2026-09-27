-- A task can wait for one other task: none of it is planned before that one's last block ends.
-- Deleting the task waited for lifts the wait rather than deleting the task that waits.
ALTER TABLE task ADD COLUMN after_task_id BIGINT REFERENCES task (id) ON DELETE SET NULL;
CREATE INDEX idx_task_after_task_id ON task (after_task_id);
