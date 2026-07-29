CREATE TABLE bill_number_skips (
    id          BIGSERIAL PRIMARY KEY,
    business    VARCHAR(50) NOT NULL,
    bill_number VARCHAR(20) NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_bill_number_skips UNIQUE (business, bill_number)
);

CREATE INDEX idx_bill_number_skips_business ON bill_number_skips(business);
