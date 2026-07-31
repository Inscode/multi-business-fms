CREATE TABLE damage_dispatches (
    id            BIGSERIAL     PRIMARY KEY,
    business      VARCHAR(50)   NOT NULL,
    dispatch_date DATE          NOT NULL,
    total_value   NUMERIC(15,2) NOT NULL DEFAULT 0,
    notes         TEXT,
    entered_by    BIGINT        NOT NULL REFERENCES users(id),
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE damage_dispatch_items (
    id           BIGSERIAL     PRIMARY KEY,
    dispatch_id  BIGINT        NOT NULL REFERENCES damage_dispatches(id) ON DELETE CASCADE,
    product_id   BIGINT        NOT NULL REFERENCES return_products(id),
    product_name VARCHAR(200)  NOT NULL,
    unit_price   NUMERIC(15,2) NOT NULL,
    quantity     INTEGER       NOT NULL,
    line_total   NUMERIC(15,2) NOT NULL
);

CREATE INDEX idx_damage_dispatches_business ON damage_dispatches(business);
CREATE INDEX idx_damage_dispatch_items_dispatch ON damage_dispatch_items(dispatch_id);
