-- ============================================================
-- V19: All new tables added after V18
-- Combines: cash_funds, missing tables (salary, stock, shop,
--           expenses), worker_tab_purchases, worker_advance_bonus,
--           petty_cash_deductions, tab_purchase_items,
--           worker_salary_deductions
-- ============================================================

-- ── Cash funds ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cash_funds (
    id          BIGSERIAL PRIMARY KEY,
    amount      DECIMAL(15,2) NOT NULL,
    date        DATE NOT NULL,
    description VARCHAR(500),
    added_by_id BIGINT NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    FOREIGN KEY (added_by_id) REFERENCES users(id)
);

-- ── Expenses ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS expenses (
    id             BIGSERIAL PRIMARY KEY,
    business       VARCHAR(50)    NOT NULL,
    category       VARCHAR(100)   NOT NULL,
    amount         DECIMAL(15,2)  NOT NULL,
    date           DATE           NOT NULL,
    description    VARCHAR(500),
    worker_id      BIGINT,
    status         VARCHAR(50)    NOT NULL,
    entered_by_id  BIGINT         NOT NULL,
    reviewed_by_id BIGINT,
    reviewed_at    TIMESTAMP,
    created_at     TIMESTAMP      NOT NULL,
    FOREIGN KEY (worker_id)      REFERENCES workers(id),
    FOREIGN KEY (entered_by_id)  REFERENCES users(id),
    FOREIGN KEY (reviewed_by_id) REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_expenses_business ON expenses (business);
CREATE INDEX IF NOT EXISTS idx_expenses_status   ON expenses (status);
CREATE INDEX IF NOT EXISTS idx_expenses_date     ON expenses (date);

-- ── Salary recipients ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS salary_recipients (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(255)   NOT NULL,
    designation    VARCHAR(100)   NOT NULL,
    monthly_salary DECIMAL(15,2)  NOT NULL,
    worker_id      BIGINT,
    user_id        BIGINT,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP      NOT NULL,
    FOREIGN KEY (worker_id) REFERENCES workers(id),
    FOREIGN KEY (user_id)   REFERENCES users(id)
);

-- ── Salary payments ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS salary_payments (
    id               BIGSERIAL PRIMARY KEY,
    recipient_id     BIGINT         NOT NULL,
    month            VARCHAR(20)    NOT NULL,
    amount           DECIMAL(15,2)  NOT NULL,
    payment_date     DATE           NOT NULL,
    notes            VARCHAR(500),
    status           VARCHAR(50)    NOT NULL,
    is_over_salary   BOOLEAN,
    entered_by_id    BIGINT         NOT NULL,
    reviewed_by_id   BIGINT,
    reviewed_at      TIMESTAMP,
    rejection_reason VARCHAR(500),
    created_at       TIMESTAMP      NOT NULL,
    FOREIGN KEY (recipient_id)    REFERENCES salary_recipients(id),
    FOREIGN KEY (entered_by_id)   REFERENCES users(id),
    FOREIGN KEY (reviewed_by_id)  REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_salary_payments_recipient ON salary_payments (recipient_id);
CREATE INDEX IF NOT EXISTS idx_salary_payments_month     ON salary_payments (month);
CREATE INDEX IF NOT EXISTS idx_salary_payments_status    ON salary_payments (status);

-- ── Shop handovers ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS shop_handovers (
    id                  BIGSERIAL PRIMARY KEY,
    bill_id             BIGINT    NOT NULL UNIQUE,
    assigned_worker_id  BIGINT,
    assigned_by         BIGINT,
    store_received_by   BIGINT,
    assigned_at         TIMESTAMP,
    shop_received_at    TIMESTAMP,
    store_received_at   TIMESTAMP,
    notes               VARCHAR(500),
    created_at          TIMESTAMP,
    FOREIGN KEY (bill_id)            REFERENCES bills(id),
    FOREIGN KEY (assigned_worker_id) REFERENCES workers(id),
    FOREIGN KEY (assigned_by)        REFERENCES users(id),
    FOREIGN KEY (store_received_by)  REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_shop_handovers_bill_id ON shop_handovers (bill_id);

-- ── Summary load bill products ───────────────────────────────
CREATE TABLE IF NOT EXISTS summary_load_bill_products (
    id                   BIGSERIAL PRIMARY KEY,
    summary_load_bill_id BIGINT    NOT NULL,
    product_id           BIGINT    NOT NULL,
    quantity             BIGINT    NOT NULL,
    created_at           TIMESTAMP NOT NULL,
    FOREIGN KEY (summary_load_bill_id) REFERENCES summary_load_bills(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id)           REFERENCES return_products(id)
);
CREATE INDEX IF NOT EXISTS idx_slbp_summary_id ON summary_load_bill_products (summary_load_bill_id);
CREATE INDEX IF NOT EXISTS idx_slbp_product_id ON summary_load_bill_products (product_id);

-- ── Stock in requests ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS stock_in_requests (
    id               BIGSERIAL PRIMARY KEY,
    reference_number VARCHAR(100),
    stock_date       DATE        NOT NULL,
    notes            TEXT,
    status           VARCHAR(50) NOT NULL,
    submitted_by     BIGINT      NOT NULL,
    approved_by      BIGINT,
    approved_at      TIMESTAMP,
    rejection_reason VARCHAR(500),
    created_at       TIMESTAMP   NOT NULL,
    updated_at       TIMESTAMP,
    FOREIGN KEY (submitted_by) REFERENCES users(id),
    FOREIGN KEY (approved_by)  REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_sir_status     ON stock_in_requests (status);
CREATE INDEX IF NOT EXISTS idx_sir_stock_date ON stock_in_requests (stock_date);

-- ── Stock in request items ───────────────────────────────────
CREATE TABLE IF NOT EXISTS stock_in_request_items (
    id                  BIGSERIAL PRIMARY KEY,
    stock_in_request_id BIGINT    NOT NULL,
    product_id          BIGINT    NOT NULL,
    quantity            BIGINT    NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    FOREIGN KEY (stock_in_request_id) REFERENCES stock_in_requests(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id)          REFERENCES return_products(id)
);
CREATE INDEX IF NOT EXISTS idx_siri_request_id ON stock_in_request_items (stock_in_request_id);
CREATE INDEX IF NOT EXISTS idx_siri_product_id ON stock_in_request_items (product_id);

-- ── Worker tab purchases (bill header) ───────────────────────
-- description/quantity/unit_price kept nullable (legacy single-item columns)
-- actual items stored in worker_tab_purchase_items
CREATE TABLE IF NOT EXISTS worker_tab_purchases (
    id                   BIGSERIAL      PRIMARY KEY,
    recipient_id         BIGINT         NOT NULL,
    description          VARCHAR(255),
    quantity             DECIMAL(10,2),
    unit_price           DECIMAL(15,2),
    total_amount         DECIMAL(15,2)  NOT NULL,
    purchase_date        DATE           NOT NULL,
    month                VARCHAR(7)     NOT NULL,
    status               VARCHAR(50)    NOT NULL DEFAULT 'PENDING_OWNER',
    notes                VARCHAR(500),
    entered_by_id        BIGINT         NOT NULL,
    owner_reviewed_by    BIGINT,
    owner_reviewed_at    TIMESTAMP,
    rejection_reason     VARCHAR(500),
    admin_confirmed_by   BIGINT,
    admin_confirmed_at   TIMESTAMP,
    created_at           TIMESTAMP      NOT NULL,
    FOREIGN KEY (recipient_id)       REFERENCES salary_recipients(id),
    FOREIGN KEY (entered_by_id)      REFERENCES users(id),
    FOREIGN KEY (owner_reviewed_by)  REFERENCES users(id),
    FOREIGN KEY (admin_confirmed_by) REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_wtp_recipient_id ON worker_tab_purchases (recipient_id);
CREATE INDEX IF NOT EXISTS idx_wtp_status       ON worker_tab_purchases (status);
CREATE INDEX IF NOT EXISTS idx_wtp_month        ON worker_tab_purchases (month);

-- ── Worker tab purchase items (bill line items) ───────────────
CREATE TABLE IF NOT EXISTS worker_tab_purchase_items (
    id          BIGSERIAL      PRIMARY KEY,
    purchase_id BIGINT         NOT NULL,
    description VARCHAR(255)   NOT NULL,
    quantity    DECIMAL(10,2)  NOT NULL,
    unit_price  DECIMAL(15,2)  NOT NULL,
    line_total  DECIMAL(15,2)  NOT NULL,
    created_at  TIMESTAMP      NOT NULL,
    FOREIGN KEY (purchase_id) REFERENCES worker_tab_purchases(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_wtpi_purchase_id ON worker_tab_purchase_items (purchase_id);

-- ── Worker advances and bonuses ───────────────────────────────
CREATE TABLE IF NOT EXISTS worker_advance_bonus (
    id                   BIGSERIAL      PRIMARY KEY,
    recipient_id         BIGINT         NOT NULL,
    type                 VARCHAR(10)    NOT NULL,
    amount               DECIMAL(15,2)  NOT NULL,
    reason               VARCHAR(500)   NOT NULL,
    month                VARCHAR(7)     NOT NULL,
    payment_date         DATE,
    status               VARCHAR(50)    NOT NULL DEFAULT 'PENDING_OWNER',
    recovered_amount     DECIMAL(15,2)  NOT NULL DEFAULT 0,
    fully_recovered      BOOLEAN        NOT NULL DEFAULT FALSE,
    notes                VARCHAR(500),
    entered_by_id        BIGINT         NOT NULL,
    owner_reviewed_by    BIGINT,
    owner_reviewed_at    TIMESTAMP,
    rejection_reason     VARCHAR(500),
    admin_confirmed_by   BIGINT,
    admin_confirmed_at   TIMESTAMP,
    created_at           TIMESTAMP      NOT NULL,
    FOREIGN KEY (recipient_id)       REFERENCES salary_recipients(id),
    FOREIGN KEY (entered_by_id)      REFERENCES users(id),
    FOREIGN KEY (owner_reviewed_by)  REFERENCES users(id),
    FOREIGN KEY (admin_confirmed_by) REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_wab_recipient_id ON worker_advance_bonus (recipient_id);
CREATE INDEX IF NOT EXISTS idx_wab_status       ON worker_advance_bonus (status);
CREATE INDEX IF NOT EXISTS idx_wab_month        ON worker_advance_bonus (month);
CREATE INDEX IF NOT EXISTS idx_wab_type         ON worker_advance_bonus (type);

-- ── Petty cash deductions ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS petty_cash_deductions (
    id               BIGSERIAL      PRIMARY KEY,
    reference_type   VARCHAR(20)    NOT NULL,
    reference_id     BIGINT         NOT NULL,
    recipient_name   VARCHAR(255)   NOT NULL,
    amount           DECIMAL(15,2)  NOT NULL,
    description      VARCHAR(500),
    deducted_by_id   BIGINT         NOT NULL,
    created_at       TIMESTAMP      NOT NULL,
    FOREIGN KEY (deducted_by_id) REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_pcd_reference  ON petty_cash_deductions (reference_type, reference_id);
CREATE INDEX IF NOT EXISTS idx_pcd_created_at ON petty_cash_deductions (created_at);

-- ── Worker salary deductions ──────────────────────────────────
CREATE TABLE IF NOT EXISTS worker_salary_deductions (
    id               BIGSERIAL      PRIMARY KEY,
    recipient_id     BIGINT         NOT NULL,
    deduction_month  VARCHAR(7)     NOT NULL,
    amount           DECIMAL(15,2)  NOT NULL,
    deduction_type   VARCHAR(30)    NOT NULL,
    reference_id     BIGINT         NOT NULL,
    description      VARCHAR(500),
    created_by_id    BIGINT         NOT NULL,
    created_at       TIMESTAMP      NOT NULL,
    FOREIGN KEY (recipient_id)   REFERENCES salary_recipients(id),
    FOREIGN KEY (created_by_id)  REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_wsd_recipient_month ON worker_salary_deductions (recipient_id, deduction_month);
CREATE INDEX IF NOT EXISTS idx_wsd_deduction_type  ON worker_salary_deductions (deduction_type);
