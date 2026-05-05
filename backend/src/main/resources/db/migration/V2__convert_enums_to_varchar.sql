-- ============================================================
-- V2: Convert PostgreSQL custom ENUM types to VARCHAR
-- ============================================================

-- Step 1: Drop all defaults that depend on ENUM types
ALTER TABLE bills ALTER COLUMN bill_source DROP DEFAULT;
ALTER TABLE bills ALTER COLUMN status DROP DEFAULT;
ALTER TABLE bills ALTER COLUMN bill_type DROP DEFAULT;
ALTER TABLE cheque_details ALTER COLUMN cheque_status DROP DEFAULT;
ALTER TABLE returns ALTER COLUMN status DROP DEFAULT;
ALTER TABLE returns ALTER COLUMN return_type DROP DEFAULT;
ALTER TABLE petty_cash ALTER COLUMN status DROP DEFAULT;
ALTER TABLE petty_cash ALTER COLUMN category DROP DEFAULT;
ALTER TABLE shop_stock_transfers ALTER COLUMN status DROP DEFAULT;
ALTER TABLE inter_business_transfers ALTER COLUMN status DROP DEFAULT;
ALTER TABLE supplier_payments ALTER COLUMN cheque_status DROP DEFAULT;
ALTER TABLE bounce_recoveries ALTER COLUMN status DROP DEFAULT;
ALTER TABLE salary_records ALTER COLUMN status DROP DEFAULT;
ALTER TABLE leave_records ALTER COLUMN status DROP DEFAULT;
ALTER TABLE leave_records ALTER COLUMN leave_type DROP DEFAULT;
ALTER TABLE workers ALTER COLUMN assignment DROP DEFAULT;
ALTER TABLE workers ALTER COLUMN business DROP DEFAULT;
ALTER TABLE workers ALTER COLUMN division DROP DEFAULT;
ALTER TABLE users ALTER COLUMN role DROP DEFAULT;

-- Step 2: Alter all ENUM columns to VARCHAR
ALTER TABLE users ALTER COLUMN role TYPE VARCHAR(50);

ALTER TABLE workers ALTER COLUMN assignment TYPE VARCHAR(50);
ALTER TABLE workers ALTER COLUMN business TYPE VARCHAR(50);
ALTER TABLE workers ALTER COLUMN division TYPE VARCHAR(50);

ALTER TABLE bills ALTER COLUMN business TYPE VARCHAR(50);
ALTER TABLE bills ALTER COLUMN division TYPE VARCHAR(50);
ALTER TABLE bills ALTER COLUMN bill_type TYPE VARCHAR(50);
ALTER TABLE bills ALTER COLUMN bill_source TYPE VARCHAR(50);
ALTER TABLE bills ALTER COLUMN status TYPE VARCHAR(50);
ALTER TABLE bills ALTER COLUMN assigned_to TYPE VARCHAR(50);

ALTER TABLE cheque_details ALTER COLUMN cheque_status TYPE VARCHAR(50);

ALTER TABLE returns ALTER COLUMN return_type TYPE VARCHAR(50);
ALTER TABLE returns ALTER COLUMN status TYPE VARCHAR(50);
ALTER TABLE returns ALTER COLUMN delivery_worker TYPE VARCHAR(50);

ALTER TABLE supplier_payments ALTER COLUMN business TYPE VARCHAR(50);
ALTER TABLE supplier_payments ALTER COLUMN division TYPE VARCHAR(50);
ALTER TABLE supplier_payments ALTER COLUMN cheque_status TYPE VARCHAR(50);

ALTER TABLE petty_cash ALTER COLUMN business TYPE VARCHAR(50);
ALTER TABLE petty_cash ALTER COLUMN division TYPE VARCHAR(50);
ALTER TABLE petty_cash ALTER COLUMN category TYPE VARCHAR(50);
ALTER TABLE petty_cash ALTER COLUMN status TYPE VARCHAR(50);

ALTER TABLE leave_records ALTER COLUMN leave_type TYPE VARCHAR(50);
ALTER TABLE leave_records ALTER COLUMN status TYPE VARCHAR(50);

ALTER TABLE salary_records ALTER COLUMN status TYPE VARCHAR(50);

ALTER TABLE bounce_recoveries ALTER COLUMN status TYPE VARCHAR(50);

ALTER TABLE shop_stock_transfers ALTER COLUMN status TYPE VARCHAR(50);

ALTER TABLE inter_business_transfers
    DROP CONSTRAINT inter_business_transfers_check;
ALTER TABLE inter_business_transfers ALTER COLUMN from_business TYPE VARCHAR(50);
ALTER TABLE inter_business_transfers ALTER COLUMN to_business TYPE VARCHAR(50);
ALTER TABLE inter_business_transfers ALTER COLUMN status TYPE VARCHAR(50);
ALTER TABLE inter_business_transfers
    ADD CONSTRAINT inter_business_transfers_check
    CHECK (from_business <> to_business);

ALTER TABLE daily_reconciliation ALTER COLUMN business TYPE VARCHAR(50);

ALTER TABLE system_bill_summaries ALTER COLUMN business TYPE VARCHAR(50);

-- Step 3: Drop all ENUM types (now safe since no defaults remain)
DROP TYPE IF EXISTS user_role;
DROP TYPE IF EXISTS business_name;
DROP TYPE IF EXISTS division;
DROP TYPE IF EXISTS bill_type;
DROP TYPE IF EXISTS bill_source;
DROP TYPE IF EXISTS bill_status;
DROP TYPE IF EXISTS worker_assignment;
DROP TYPE IF EXISTS cheque_status;
DROP TYPE IF EXISTS return_type;
DROP TYPE IF EXISTS return_status;
DROP TYPE IF EXISTS supplier_payment_status;
DROP TYPE IF EXISTS transfer_status;
DROP TYPE IF EXISTS leave_type;
DROP TYPE IF EXISTS leave_status;
DROP TYPE IF EXISTS salary_status;
DROP TYPE IF EXISTS expense_category;
DROP TYPE IF EXISTS bounce_recovery_status;
DROP TYPE IF EXISTS stock_transfer_status;
DROP TYPE IF EXISTS petty_cash_status;

-- Step 4: Restore defaults as plain strings
ALTER TABLE bills ALTER COLUMN bill_source SET DEFAULT 'MANUAL';
ALTER TABLE bills ALTER COLUMN status SET DEFAULT 'DRAFT';
ALTER TABLE cheque_details ALTER COLUMN cheque_status SET DEFAULT 'DETAILS_PENDING';
ALTER TABLE returns ALTER COLUMN status SET DEFAULT 'ENTERED';
ALTER TABLE petty_cash ALTER COLUMN status SET DEFAULT 'ENTERED';
ALTER TABLE shop_stock_transfers ALTER COLUMN status SET DEFAULT 'ENTERED';
ALTER TABLE inter_business_transfers ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE bounce_recoveries ALTER COLUMN status SET DEFAULT 'OPEN';
ALTER TABLE salary_records ALTER COLUMN status SET DEFAULT 'DRAFT';
ALTER TABLE leave_records ALTER COLUMN status SET DEFAULT 'PENDING';