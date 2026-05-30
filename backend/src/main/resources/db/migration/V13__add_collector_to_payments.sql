ALTER TABLE payments
    ADD COLUMN collected_by_worker_id BIGINT REFERENCES workers(id),
    ADD COLUMN collector_note         TEXT;

CREATE INDEX idx_payments_collected_by_worker ON payments(collected_by_worker_id);