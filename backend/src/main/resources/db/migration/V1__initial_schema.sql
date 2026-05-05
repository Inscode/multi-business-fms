-- ============================================================
-- Multi-Business Financial Management System
-- Flyway Migration: V1__initial_schema.sql
-- ============================================================

-- ============================================================
-- ENUMS
-- ============================================================

CREATE TYPE business_name AS ENUM (
    'RAINCO',
    'STATIONERY',
    'PLASTIC',
    'HARDWARE'
);

CREATE TYPE division AS ENUM (
    'STORE',
    'SHOP'
);

CREATE TYPE user_role AS ENUM (
    'ADMIN',
    'OWNER',
    'ACCOUNTANT'
);

CREATE TYPE bill_type AS ENUM (
    'CASH',
    'CHEQUE'
);

CREATE TYPE bill_source AS ENUM (
    'SYSTEM',
    'MANUAL',
    'DRAFT'
);

CREATE TYPE bill_status AS ENUM (
    'DRAFT',
    'STOCK_ENTERED',
    'ASSIGNED',
    'RECEIVED',
    'CONFIRMED',
    'CANCELLED'
);

CREATE TYPE worker_assignment AS ENUM (
    'WORKER_A',
    'WORKER_B',
    'REPRESENTATIVE',
    'SHOP',
    'OTHER'
);

CREATE TYPE cheque_status AS ENUM (
    'DETAILS_PENDING',
    'DETAILS_COMPLETE',
    'DEPOSITED',
    'CLEARED',
    'BOUNCED'
);

CREATE TYPE return_type AS ENUM (
    'RESELLABLE',
    'DAMAGED'
);

CREATE TYPE return_status AS ENUM (
    'ENTERED',
    'RECEIVED',
    'CONFIRMED',
    'STOCK_RESTORED',
    'DAMAGE_LOGGED'
);

CREATE TYPE supplier_payment_status AS ENUM (
    'ISSUED',
    'CLEARED',
    'BOUNCED'
);

CREATE TYPE transfer_status AS ENUM (
    'PENDING',
    'SETTLED'
);

CREATE TYPE leave_type AS ENUM (
    'FULL_DAY',
    'HALF_DAY',
    'MEDICAL',
    'ANNUAL',
    'NO_PAY'
);

CREATE TYPE leave_status AS ENUM (
    'PENDING',
    'APPROVED',
    'REJECTED'
);

CREATE TYPE salary_status AS ENUM (
    'DRAFT',
    'APPROVED',
    'PAID'
);

CREATE TYPE expense_category AS ENUM (
    'TEA_AND_MEALS',
    'TRANSPORT',
    'STATIONERY',
    'REPAIRS',
    'MISCELLANEOUS'
);

CREATE TYPE bounce_recovery_status AS ENUM (
    'OPEN',
    'PARTIALLY_RECOVERED',
    'RECOVERED',
    'WRITTEN_OFF'
);

CREATE TYPE stock_transfer_status AS ENUM (
    'ENTERED',
    'CONFIRMED'
);

CREATE TYPE petty_cash_status AS ENUM (
    'ENTERED',
    'CONFIRMED'
);

-- ============================================================
-- USERS
-- ============================================================

CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(200) NOT NULL,
    role          user_role    NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- WORKERS
-- No system login — managed by admin via UI
-- ============================================================

CREATE TABLE workers (
    id            BIGSERIAL          PRIMARY KEY,
    full_name     VARCHAR(200)       NOT NULL,
    assignment    worker_assignment  NOT NULL,
    business      business_name      NOT NULL,
    division      division           NOT NULL,
    base_salary   DECIMAL(12,2)      NOT NULL DEFAULT 0,
    active        BOOLEAN            NOT NULL DEFAULT TRUE,
    joined_date   DATE               NOT NULL,
    notes         TEXT,
    created_at    TIMESTAMP          NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP          NOT NULL DEFAULT NOW()
);

-- ============================================================
-- PRODUCTS (Rainco only)
-- ============================================================

CREATE TABLE products (
    id               BIGSERIAL     PRIMARY KEY,
    name             VARCHAR(200)  NOT NULL,
    unit             VARCHAR(50)   NOT NULL DEFAULT 'units',
    current_stock    INT           NOT NULL DEFAULT 0,
    low_stock_alert  INT           NOT NULL DEFAULT 50,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- ============================================================
-- STOCK RECEIPTS
-- ============================================================

CREATE TABLE stock_receipts (
    id               BIGSERIAL     PRIMARY KEY,
    product_id       BIGINT        NOT NULL REFERENCES products(id),
    quantity         INT           NOT NULL,
    unit_cost        DECIMAL(12,2) NOT NULL,
    total_cost       DECIMAL(12,2) NOT NULL,
    received_date    DATE          NOT NULL DEFAULT CURRENT_DATE,
    supplier_name    VARCHAR(200),
    invoice_number   VARCHAR(100),
    entered_by       BIGINT        NOT NULL REFERENCES users(id),
    notes            TEXT,
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stock_receipts_product ON stock_receipts(product_id);
CREATE INDEX idx_stock_receipts_date    ON stock_receipts(received_date);

-- ============================================================
-- BILLS
-- ============================================================

CREATE TABLE bills (
    id              BIGSERIAL         PRIMARY KEY,
    bill_number     VARCHAR(100),
    business        business_name     NOT NULL,
    division        division          NOT NULL,
    bill_type       bill_type         NOT NULL,
    bill_source     bill_source       NOT NULL DEFAULT 'MANUAL',
    customer_name   VARCHAR(200)      NOT NULL,
    total_amount    DECIMAL(12,2)     NOT NULL,
    status          bill_status       NOT NULL DEFAULT 'DRAFT',
    assigned_to     worker_assignment,
    worker_id       BIGINT            REFERENCES workers(id),
    entered_by      BIGINT            NOT NULL REFERENCES users(id),
    received_by     BIGINT            REFERENCES users(id),
    received_at     TIMESTAMP,
    confirmed_by    BIGINT            REFERENCES users(id),
    confirmed_at    TIMESTAMP,
    bill_date       DATE              NOT NULL DEFAULT CURRENT_DATE,
    notes           TEXT,
    created_at      TIMESTAMP         NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP         NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bills_business    ON bills(business);
CREATE INDEX idx_bills_status      ON bills(status);
CREATE INDEX idx_bills_bill_date   ON bills(bill_date);
CREATE INDEX idx_bills_worker_id   ON bills(worker_id);
CREATE INDEX idx_bills_assigned_to ON bills(assigned_to);
CREATE INDEX idx_bills_source      ON bills(bill_source);

-- ============================================================
-- BILL ITEMS
-- ============================================================

CREATE TABLE bill_items (
    id            BIGSERIAL     PRIMARY KEY,
    bill_id       BIGINT        NOT NULL REFERENCES bills(id) ON DELETE CASCADE,
    product_id    BIGINT        REFERENCES products(id),
    product_name  VARCHAR(200)  NOT NULL,
    quantity      INT           NOT NULL,
    unit_price    DECIMAL(12,2) NOT NULL,
    total_price   DECIMAL(12,2) NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bill_items_bill_id    ON bill_items(bill_id);
CREATE INDEX idx_bill_items_product_id ON bill_items(product_id);

-- ============================================================
-- SYSTEM BILL SUMMARIES (Rainco monthly)
-- ============================================================

CREATE TABLE system_bill_summaries (
    id           BIGSERIAL     PRIMARY KEY,
    business     business_name NOT NULL,
    month        INT           NOT NULL,
    year         INT           NOT NULL,
    entered_by   BIGINT        NOT NULL REFERENCES users(id),
    confirmed_by BIGINT        REFERENCES users(id),
    confirmed_at TIMESTAMP,
    notes        TEXT,
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    UNIQUE(business, month, year)
);

CREATE TABLE system_summary_items (
    id             BIGSERIAL     PRIMARY KEY,
    summary_id     BIGINT        NOT NULL REFERENCES system_bill_summaries(id) ON DELETE CASCADE,
    product_id     BIGINT        NOT NULL REFERENCES products(id),
    total_quantity INT           NOT NULL,
    avg_unit_price DECIMAL(12,2) NOT NULL,
    total_amount   DECIMAL(12,2) NOT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE system_bill_references (
    id          BIGSERIAL    PRIMARY KEY,
    summary_id  BIGINT       NOT NULL REFERENCES system_bill_summaries(id) ON DELETE CASCADE,
    bill_number VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sys_bill_ref_summary ON system_bill_references(summary_id);
CREATE INDEX idx_sys_bill_ref_number  ON system_bill_references(bill_number);

-- ============================================================
-- BILL SYSTEM LINKS
-- Links manual/draft bills to system bill numbers
-- ============================================================

CREATE TABLE bill_system_links (
    id                 BIGSERIAL    PRIMARY KEY,
    bill_id            BIGINT       NOT NULL REFERENCES bills(id),
    system_bill_number VARCHAR(100) NOT NULL,
    linked_by          BIGINT       NOT NULL REFERENCES users(id),
    linked_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bill_links_bill_id ON bill_system_links(bill_id);
CREATE INDEX idx_bill_links_number  ON bill_system_links(system_bill_number);

-- ============================================================
-- CHEQUE DETAILS
-- ============================================================

CREATE TABLE cheque_details (
    id                 BIGSERIAL     PRIMARY KEY,
    bill_id            BIGINT        NOT NULL UNIQUE REFERENCES bills(id) ON DELETE CASCADE,
    cheque_number      VARCHAR(100),
    bank_name          VARCHAR(200),
    branch_name        VARCHAR(200),
    cheque_date        DATE,
    cheque_status      cheque_status NOT NULL DEFAULT 'DETAILS_PENDING',
    details_entered_by BIGINT        REFERENCES users(id),
    details_entered_at TIMESTAMP,
    deposited_date     DATE,
    deposited_by       BIGINT        REFERENCES users(id),
    cleared_date       DATE,
    cleared_amount     DECIMAL(12,2),
    bounced_date       DATE,
    bounced_reason     TEXT,
    created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cheque_status    ON cheque_details(cheque_status);
CREATE INDEX idx_cheque_date      ON cheque_details(cheque_date);
CREATE INDEX idx_cheque_deposited ON cheque_details(deposited_date);

-- ============================================================
-- CHEQUE BOUNCE RECOVERY
-- ============================================================

CREATE TABLE bounce_recoveries (
    id               BIGSERIAL              PRIMARY KEY,
    cheque_detail_id BIGINT                 NOT NULL REFERENCES cheque_details(id),
    bill_id          BIGINT                 NOT NULL REFERENCES bills(id),
    original_amount  DECIMAL(12,2)          NOT NULL,
    recovered_amount DECIMAL(12,2)          NOT NULL DEFAULT 0,
    remaining_amount DECIMAL(12,2)          NOT NULL,
    status           bounce_recovery_status NOT NULL DEFAULT 'OPEN',
    written_off_by   BIGINT                 REFERENCES users(id),
    written_off_at   TIMESTAMP,
    notes            TEXT,
    created_at       TIMESTAMP              NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP              NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bounce_recovery_status ON bounce_recoveries(status);

-- ============================================================
-- RETURNS
-- ============================================================

CREATE TABLE returns (
    id              BIGSERIAL         PRIMARY KEY,
    bill_id         BIGINT            NOT NULL REFERENCES bills(id),
    invoice_number  VARCHAR(100)      NOT NULL,
    return_type     return_type       NOT NULL,
    status          return_status     NOT NULL DEFAULT 'ENTERED',
    delivery_worker worker_assignment,
    total_value     DECIMAL(12,2)     NOT NULL,
    entered_by      BIGINT            NOT NULL REFERENCES users(id),
    received_by     BIGINT            REFERENCES users(id),
    received_at     TIMESTAMP,
    confirmed_by    BIGINT            REFERENCES users(id),
    confirmed_at    TIMESTAMP,
    return_date     DATE              NOT NULL DEFAULT CURRENT_DATE,
    notes           TEXT,
    created_at      TIMESTAMP         NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP         NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_returns_bill_id  ON returns(bill_id);
CREATE INDEX idx_returns_status   ON returns(status);
CREATE INDEX idx_returns_invoice  ON returns(invoice_number);
CREATE INDEX idx_returns_date     ON returns(return_date);

CREATE TABLE return_items (
    id           BIGSERIAL     PRIMARY KEY,
    return_id    BIGINT        NOT NULL REFERENCES returns(id) ON DELETE CASCADE,
    product_id   BIGINT        REFERENCES products(id),
    product_name VARCHAR(200)  NOT NULL,
    quantity     INT           NOT NULL,
    unit_price   DECIMAL(12,2) NOT NULL,
    total_price  DECIMAL(12,2) NOT NULL,
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_return_items_return_id ON return_items(return_id);

-- ============================================================
-- DAMAGE LOG
-- ============================================================

CREATE TABLE damage_log (
    id             BIGSERIAL     PRIMARY KEY,
    return_id      BIGINT        NOT NULL REFERENCES returns(id),
    product_id     BIGINT        REFERENCES products(id),
    product_name   VARCHAR(200)  NOT NULL,
    quantity       INT           NOT NULL,
    estimated_loss DECIMAL(12,2),
    logged_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    notes          TEXT
);

-- ============================================================
-- SHOP STOCK TRANSFERS
-- ============================================================

CREATE TABLE shop_stock_transfers (
    id             BIGSERIAL             PRIMARY KEY,
    product_id     BIGINT                NOT NULL REFERENCES products(id),
    quantity       INT                   NOT NULL,
    transfer_value DECIMAL(12,2)         NOT NULL,
    transfer_date  DATE                  NOT NULL DEFAULT CURRENT_DATE,
    status         stock_transfer_status NOT NULL DEFAULT 'ENTERED',
    entered_by     BIGINT                NOT NULL REFERENCES users(id),
    confirmed_by   BIGINT                REFERENCES users(id),
    confirmed_at   TIMESTAMP,
    notes          TEXT,
    created_at     TIMESTAMP             NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP             NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_shop_transfers_product ON shop_stock_transfers(product_id);
CREATE INDEX idx_shop_transfers_date    ON shop_stock_transfers(transfer_date);

-- ============================================================
-- RETAIL SHOP DAILY CASH
-- ============================================================

CREATE TABLE retail_daily_cash (
    id               BIGSERIAL     PRIMARY KEY,
    cash_date        DATE          NOT NULL DEFAULT CURRENT_DATE,
    amount_collected DECIMAL(12,2) NOT NULL,
    entered_by       BIGINT        NOT NULL REFERENCES users(id),
    confirmed_by     BIGINT        REFERENCES users(id),
    confirmed_at     TIMESTAMP,
    notes            TEXT,
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    UNIQUE(cash_date)
);

-- ============================================================
-- SUPPLIER PAYMENTS
-- ============================================================

CREATE TABLE supplier_payments (
    id            BIGSERIAL               PRIMARY KEY,
    business      business_name           NOT NULL,
    division      division                NOT NULL,
    supplier_name VARCHAR(200)            NOT NULL,
    description   TEXT                    NOT NULL,
    amount        DECIMAL(12,2)           NOT NULL,
    payment_date  DATE                    NOT NULL DEFAULT CURRENT_DATE,
    is_cheque     BOOLEAN                 NOT NULL DEFAULT FALSE,
    cheque_number VARCHAR(100),
    bank_name     VARCHAR(200),
    branch_name   VARCHAR(200),
    cheque_date   DATE,
    cheque_status supplier_payment_status,
    cleared_date  DATE,
    bounced_date  DATE,
    bounced_reason TEXT,
    entered_by    BIGINT                  NOT NULL REFERENCES users(id),
    confirmed_by  BIGINT                  REFERENCES users(id),
    confirmed_at  TIMESTAMP,
    notes         TEXT,
    created_at    TIMESTAMP               NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP               NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_supplier_payments_business ON supplier_payments(business);
CREATE INDEX idx_supplier_payments_date     ON supplier_payments(payment_date);

-- ============================================================
-- PETTY CASH
-- ============================================================

CREATE TABLE petty_cash (
    id           BIGSERIAL         PRIMARY KEY,
    business     business_name     NOT NULL,
    division     division          NOT NULL,
    expense_date DATE              NOT NULL DEFAULT CURRENT_DATE,
    category     expense_category  NOT NULL,
    amount       DECIMAL(12,2)     NOT NULL,
    description  TEXT,
    status       petty_cash_status NOT NULL DEFAULT 'ENTERED',
    entered_by   BIGINT            NOT NULL REFERENCES users(id),
    confirmed_by BIGINT            REFERENCES users(id),
    confirmed_at TIMESTAMP,
    created_at   TIMESTAMP         NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP         NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_petty_cash_business ON petty_cash(business);
CREATE INDEX idx_petty_cash_date     ON petty_cash(expense_date);

-- ============================================================
-- ATTENDANCE
-- ============================================================

CREATE TABLE attendance (
    id          BIGSERIAL PRIMARY KEY,
    worker_id   BIGINT    NOT NULL REFERENCES workers(id),
    attend_date DATE      NOT NULL,
    present     BOOLEAN   NOT NULL DEFAULT TRUE,
    late        BOOLEAN   NOT NULL DEFAULT FALSE,
    entered_by  BIGINT    NOT NULL REFERENCES users(id),
    notes       TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(worker_id, attend_date)
);

CREATE INDEX idx_attendance_worker_id ON attendance(worker_id);
CREATE INDEX idx_attendance_date      ON attendance(attend_date);

-- ============================================================
-- LEAVE ENTITLEMENTS
-- ============================================================

CREATE TABLE leave_entitlements (
    id           BIGSERIAL PRIMARY KEY,
    worker_id    BIGINT    NOT NULL REFERENCES workers(id),
    year         INT       NOT NULL,
    annual_days  INT       NOT NULL DEFAULT 14,
    medical_days INT       NOT NULL DEFAULT 7,
    used_annual  INT       NOT NULL DEFAULT 0,
    used_medical INT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(worker_id, year)
);

-- ============================================================
-- LEAVE RECORDS
-- ============================================================

CREATE TABLE leave_records (
    id          BIGSERIAL    PRIMARY KEY,
    worker_id   BIGINT       NOT NULL REFERENCES workers(id),
    leave_date  DATE         NOT NULL,
    leave_type  leave_type   NOT NULL,
    status      leave_status NOT NULL DEFAULT 'PENDING',
    is_no_pay   BOOLEAN      NOT NULL DEFAULT FALSE,
    entered_by  BIGINT       NOT NULL REFERENCES users(id),
    approved_by BIGINT       REFERENCES users(id),
    approved_at TIMESTAMP,
    notes       TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE(worker_id, leave_date)
);

CREATE INDEX idx_leave_worker_id ON leave_records(worker_id);
CREATE INDEX idx_leave_date      ON leave_records(leave_date);
CREATE INDEX idx_leave_status    ON leave_records(status);

-- ============================================================
-- SALARY RECORDS
-- ============================================================

CREATE TABLE salary_records (
    id                  BIGSERIAL     PRIMARY KEY,
    worker_id           BIGINT        NOT NULL REFERENCES workers(id),
    month               INT           NOT NULL,
    year                INT           NOT NULL,
    base_salary         DECIMAL(12,2) NOT NULL,
    no_pay_days         INT           NOT NULL DEFAULT 0,
    no_pay_deduction    DECIMAL(12,2) NOT NULL DEFAULT 0,
    transport_allowance DECIMAL(12,2) NOT NULL DEFAULT 0,
    attendance_bonus    DECIMAL(12,2) NOT NULL DEFAULT 0,
    advance_deduction   DECIMAL(12,2) NOT NULL DEFAULT 0,
    other_deductions    DECIMAL(12,2) NOT NULL DEFAULT 0,
    net_salary          DECIMAL(12,2) NOT NULL,
    status              salary_status NOT NULL DEFAULT 'DRAFT',
    approved_by         BIGINT        REFERENCES users(id),
    approved_at         TIMESTAMP,
    paid_at             TIMESTAMP,
    paid_by             BIGINT        REFERENCES users(id),
    notes               TEXT,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    UNIQUE(worker_id, month, year)
);

CREATE INDEX idx_salary_worker_id  ON salary_records(worker_id);
CREATE INDEX idx_salary_month_year ON salary_records(month, year);

-- ============================================================
-- WORKER ADVANCES
-- ============================================================

CREATE TABLE worker_advances (
    id                BIGSERIAL     PRIMARY KEY,
    worker_id         BIGINT        NOT NULL REFERENCES workers(id),
    amount            DECIMAL(12,2) NOT NULL,
    advance_date      DATE          NOT NULL DEFAULT CURRENT_DATE,
    remaining_balance DECIMAL(12,2) NOT NULL,
    fully_recovered   BOOLEAN       NOT NULL DEFAULT FALSE,
    entered_by        BIGINT        NOT NULL REFERENCES users(id),
    confirmed_by      BIGINT        REFERENCES users(id),
    confirmed_at      TIMESTAMP,
    notes             TEXT,
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_advances_worker_id ON worker_advances(worker_id);

-- ============================================================
-- INTER-BUSINESS TRANSFERS
-- ============================================================

CREATE TABLE inter_business_transfers (
    id            BIGSERIAL       PRIMARY KEY,
    from_business business_name   NOT NULL,
    to_business   business_name   NOT NULL,
    amount        DECIMAL(12,2)   NOT NULL,
    transfer_date DATE            NOT NULL DEFAULT CURRENT_DATE,
    reason        TEXT            NOT NULL,
    status        transfer_status NOT NULL DEFAULT 'PENDING',
    settled_date  DATE,
    entered_by    BIGINT          NOT NULL REFERENCES users(id),
    confirmed_by  BIGINT          REFERENCES users(id),
    confirmed_at  TIMESTAMP,
    notes         TEXT,
    created_at    TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP       NOT NULL DEFAULT NOW(),
    CHECK (from_business <> to_business)
);

CREATE INDEX idx_transfers_from ON inter_business_transfers(from_business);
CREATE INDEX idx_transfers_to   ON inter_business_transfers(to_business);
CREATE INDEX idx_transfers_date ON inter_business_transfers(transfer_date);

-- ============================================================
-- DAILY RECONCILIATION
-- ============================================================

CREATE TABLE daily_reconciliation (
    id              BIGSERIAL     PRIMARY KEY,
    business        business_name NOT NULL,
    recon_date      DATE          NOT NULL DEFAULT CURRENT_DATE,
    expected_cash   DECIMAL(12,2) NOT NULL DEFAULT 0,
    expected_cheque DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_confirmed DECIMAL(12,2) NOT NULL DEFAULT 0,
    gap_amount      DECIMAL(12,2) NOT NULL DEFAULT 0,
    notes           TEXT,
    created_by      BIGINT        NOT NULL REFERENCES users(id),
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    UNIQUE(business, recon_date)
);


-- ============================================================
-- AUDIT LOG
-- ============================================================

CREATE TABLE audit_log (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       REFERENCES users(id),
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id   BIGINT,
    old_value   TEXT,
    new_value   TEXT,
    ip_address  VARCHAR(50),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user_id    ON audit_log(user_id);
CREATE INDEX idx_audit_entity     ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_created_at ON audit_log(created_at);

-- ============================================================
-- SEED DATA
-- All real users and workers are created via Admin UI
-- Only the system admin account is seeded here
-- Password: admin123 — MUST be changed on first login
-- bcrypt hash of 'admin123' with strength 12
-- ============================================================

INSERT INTO users (username, password_hash, full_name, role) VALUES
    ('admin', '$2a$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.', 'System Administrator', 'ADMIN');

-- Sample products for Rainco (admin can edit via UI)
INSERT INTO products (name, unit, current_stock, low_stock_alert) VALUES
    ('Umbrella', 'units', 0, 50),
    ('Raincoat', 'units', 0, 30);


