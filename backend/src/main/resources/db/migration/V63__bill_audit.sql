-- ============================================================
-- V63: Month-end bill reconciliation ("sweep").
-- Work through every bill the system still shows as owing and mark
-- what is physically in hand; the remainder is the exception list —
-- either paid-but-not-entered, or genuinely missing.
-- ============================================================

CREATE TABLE bill_audit_sessions (
    id             BIGSERIAL PRIMARY KEY,
    period_month   DATE NOT NULL,              -- first day of the month being reconciled
    business_scope VARCHAR(50),                -- null = all businesses
    area_scope     VARCHAR(100),               -- null = all areas
    opened_by_id   BIGINT REFERENCES users(id),
    opened_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    closed_at      TIMESTAMP
);

CREATE TABLE bill_audit_marks (
    id           BIGSERIAL PRIMARY KEY,
    session_id   BIGINT NOT NULL REFERENCES bill_audit_sessions(id) ON DELETE CASCADE,
    bill_id      BIGINT NOT NULL REFERENCES bills(id),
    mark_type    VARCHAR(30) NOT NULL,         -- IN_HAND | PAID_NOT_ENTERED | MISSING
    note         TEXT,
    marked_by_id BIGINT REFERENCES users(id),
    marked_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_bill_audit_mark UNIQUE (session_id, bill_id)
);

CREATE INDEX idx_bill_audit_marks_session ON bill_audit_marks(session_id);
CREATE INDEX idx_bill_audit_sessions_month ON bill_audit_sessions(period_month);
