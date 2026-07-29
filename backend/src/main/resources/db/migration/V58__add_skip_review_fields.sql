ALTER TABLE bill_number_skips
    ADD COLUMN status       VARCHAR(20)  NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN submitted_by BIGINT       REFERENCES users(id),
    ADD COLUMN bill_id      BIGINT       REFERENCES bills(id),
    ADD COLUMN reviewed_by  BIGINT       REFERENCES users(id),
    ADD COLUMN reviewed_at  TIMESTAMP;
