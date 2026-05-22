CREATE TABLE collection_notes (
    id              BIGSERIAL       PRIMARY KEY,
    bill_id         BIGINT          NOT NULL REFERENCES bills(id),
    amount          DECIMAL(12,2)   NOT NULL,
    payment_type    VARCHAR(50)     NOT NULL,
    status          VARCHAR(50)     NOT NULL DEFAULT 'PENDING',
    collected_by    BIGINT          NOT NULL REFERENCES users(id),
    collected_at    TIMESTAMP       NOT NULL DEFAULT NOW(),
    notes           TEXT,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_collection_notes_bill_id      ON collection_notes(bill_id);
CREATE INDEX idx_collection_notes_status       ON collection_notes(status);
CREATE INDEX idx_collection_notes_collected_by ON collection_notes(collected_by);

ALTER TABLE payments
    ADD COLUMN collection_note_id BIGINT REFERENCES collection_notes(id);

CREATE INDEX idx_payments_collection_note_id ON payments(collection_note_id);