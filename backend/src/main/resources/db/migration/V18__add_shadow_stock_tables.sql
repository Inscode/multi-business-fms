-- Shadow Stock Management Tables for RAINCO

-- Shadow Stock Movements table
CREATE TABLE shadow_stock_movements (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    quantity BIGINT NOT NULL,
    bill_id BIGINT,
    invoice_number VARCHAR(100),
    movement_date DATE NOT NULL,
    notes TEXT,
    entered_by BIGINT NOT NULL,
    cancelled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES return_products(id),
    FOREIGN KEY (bill_id) REFERENCES bills(id),
    FOREIGN KEY (entered_by) REFERENCES users(id)
);

CREATE INDEX idx_ssm_product_id    ON shadow_stock_movements (product_id);
CREATE INDEX idx_ssm_bill_id       ON shadow_stock_movements (bill_id);
CREATE INDEX idx_ssm_type          ON shadow_stock_movements (type);
CREATE INDEX idx_ssm_cancelled     ON shadow_stock_movements (cancelled);
CREATE INDEX idx_ssm_movement_date ON shadow_stock_movements (movement_date);

-- Bill Stock Items table (line items for bills with stock)
CREATE TABLE bill_stock_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bill_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    line_total DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES return_products(id)
);

CREATE INDEX idx_bsi_bill_id    ON bill_stock_items (bill_id);
CREATE INDEX idx_bsi_product_id ON bill_stock_items (product_id);

-- Bill Stock Links table (links DRAFT/MANUAL to SYSTEM bills)
CREATE TABLE bill_stock_links (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    system_bill_id BIGINT NOT NULL,
    child_bill_id BIGINT NOT NULL,
    linked_by BIGINT NOT NULL,
    linked_at TIMESTAMP NOT NULL,
    notes TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT uk_child_bill UNIQUE (child_bill_id),
    FOREIGN KEY (system_bill_id) REFERENCES bills(id),
    FOREIGN KEY (child_bill_id) REFERENCES bills(id),
    FOREIGN KEY (linked_by) REFERENCES users(id)
);

CREATE INDEX idx_bsl_system_bill_id ON bill_stock_links (system_bill_id);
CREATE INDEX idx_bsl_child_bill_id  ON bill_stock_links (child_bill_id);

-- Summary Load Bills table (groups SYSTEM bills for shipping)
CREATE TABLE summary_load_bills (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    load_date DATE NOT NULL,
    notes TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_by BIGINT NOT NULL,
    approved_by BIGINT,
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id),
    FOREIGN KEY (approved_by) REFERENCES users(id)
);

CREATE INDEX idx_slb_status    ON summary_load_bills (status);
CREATE INDEX idx_slb_load_date ON summary_load_bills (load_date);

-- Junction table for Summary Load Bills and System Bills
CREATE TABLE summary_load_bill_items (
    summary_load_bill_id BIGINT NOT NULL,
    system_bill_id BIGINT NOT NULL,
    PRIMARY KEY (summary_load_bill_id, system_bill_id),
    FOREIGN KEY (summary_load_bill_id) REFERENCES summary_load_bills(id) ON DELETE CASCADE,
    FOREIGN KEY (system_bill_id) REFERENCES bills(id)
);

CREATE INDEX idx_slbi_system_bill_id ON summary_load_bill_items (system_bill_id);
