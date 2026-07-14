CREATE TABLE bill_reviews (
    id          BIGSERIAL PRIMARY KEY,
    bill_id     BIGINT      NOT NULL REFERENCES bills(id),
    reviewed_by BIGINT      NOT NULL REFERENCES users(id),
    reviewed_at TIMESTAMP   NOT NULL,
    CONSTRAINT uq_bill_reviews_bill_user UNIQUE (bill_id, reviewed_by)
);

CREATE INDEX idx_bill_reviews_bill_id     ON bill_reviews(bill_id);
CREATE INDEX idx_bill_reviews_reviewed_by ON bill_reviews(reviewed_by);
