CREATE TABLE return_products (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_return_products_business ON return_products (business, active);

CREATE TABLE bill_returns (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bill_id BIGINT NOT NULL,
    return_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    items_total DECIMAL(15,2) NOT NULL,
    discount_percentage DECIMAL(5,2),
    discount_fixed DECIMAL(15,2),
    calculated_return_amount DECIMAL(15,2) NOT NULL,
    predicted_value DECIMAL(15,2),
    approved_with VARCHAR(20),
    approved_amount DECIMAL(15,2),
    rejection_reason VARCHAR(500),
    notes VARCHAR(500),
    responsible_worker_id BIGINT,
    submitted_by_id BIGINT NOT NULL,
    submitted_at TIMESTAMP NOT NULL,
    reviewed_by_id BIGINT,
    reviewed_at TIMESTAMP,
    CONSTRAINT fk_br_bill FOREIGN KEY (bill_id) REFERENCES bills(id),
    CONSTRAINT fk_br_worker FOREIGN KEY (responsible_worker_id) REFERENCES workers(id),
    CONSTRAINT fk_br_submitted_by FOREIGN KEY (submitted_by_id) REFERENCES users(id),
    CONSTRAINT fk_br_reviewed_by FOREIGN KEY (reviewed_by_id) REFERENCES users(id)
);

CREATE INDEX idx_bill_returns_bill ON bill_returns (bill_id);
CREATE INDEX idx_bill_returns_status ON bill_returns (status);

CREATE TABLE bill_return_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bill_return_id BIGINT NOT NULL,
    product_id BIGINT,
    item_name VARCHAR(255) NOT NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    quantity_requested INT NOT NULL,
    quantity_returned INT,
    line_total DECIMAL(15,2) NOT NULL,
    CONSTRAINT fk_bri_return FOREIGN KEY (bill_return_id) REFERENCES bill_returns(id),
    CONSTRAINT fk_bri_product FOREIGN KEY (product_id) REFERENCES return_products(id)
);
