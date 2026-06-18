CREATE TABLE bill_backorder_requests (
    id BIGSERIAL PRIMARY KEY,
    bill_id BIGINT NOT NULL REFERENCES bills(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submitted_by_id BIGINT REFERENCES users(id),
    reviewed_by_id BIGINT REFERENCES users(id),
    submitted_at TIMESTAMP,
    reviewed_at TIMESTAMP,
    notes VARCHAR(500),
    rejection_reason VARCHAR(500)
);

CREATE TABLE bill_backorder_items (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES bill_backorder_requests(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES return_products(id),
    quantity BIGINT NOT NULL,
    unit_price DECIMAL(19,2) NOT NULL,
    line_total DECIMAL(19,2) NOT NULL,
    amount_to_add DECIMAL(19,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL
);
