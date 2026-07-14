-- Allow admin to exclude specific workers (e.g. temporary staff) from time log
ALTER TABLE workers ADD COLUMN time_log_active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_workers_time_log_active ON workers(time_log_active) WHERE time_log_active = TRUE;
