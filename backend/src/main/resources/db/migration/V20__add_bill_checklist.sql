-- ── Bill checklist notes (individual additions throughout the day) ─
CREATE TABLE IF NOT EXISTS bill_checklist_notes (
    id             BIGSERIAL    PRIMARY KEY,
    item_name      VARCHAR(100) NOT NULL,
    count          INTEGER      NOT NULL,
    note           VARCHAR(500),
    entry_date     DATE         NOT NULL,
    created_by_id  BIGINT       NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    FOREIGN KEY (created_by_id) REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_bcn_entry_date ON bill_checklist_notes (entry_date);
CREATE INDEX IF NOT EXISTS idx_bcn_item_name  ON bill_checklist_notes (item_name);

-- ── Bill checklist closings (evening verification per item per day) ─
CREATE TABLE IF NOT EXISTS bill_checklist_closings (
    id              BIGSERIAL    PRIMARY KEY,
    item_name       VARCHAR(100) NOT NULL,
    closing_date    DATE         NOT NULL,
    marked_entered  INTEGER      NOT NULL DEFAULT 0,
    closed_by_id    BIGINT       NOT NULL,
    closed_at       TIMESTAMP    NOT NULL,
    CONSTRAINT uq_closing_item_date UNIQUE (item_name, closing_date),
    FOREIGN KEY (closed_by_id) REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_bcc_closing_date ON bill_checklist_closings (closing_date);
CREATE INDEX IF NOT EXISTS idx_bcc_item_name    ON bill_checklist_closings (item_name);
