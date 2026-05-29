CREATE TABLE bill_reminders (
    id            BIGSERIAL     PRIMARY KEY,
    bill_id       BIGINT        NOT NULL REFERENCES bills(id),
    reminder_date DATE          NOT NULL,
    period        VARCHAR(20)   NOT NULL,
    note          TEXT,
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_by    BIGINT        NOT NULL REFERENCES users(id),
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bill_reminders_bill_id       ON bill_reminders(bill_id);
CREATE INDEX idx_bill_reminders_reminder_date ON bill_reminders(reminder_date);
CREATE INDEX idx_bill_reminders_status        ON bill_reminders(status);