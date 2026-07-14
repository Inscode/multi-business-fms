ALTER TABLE workers ADD COLUMN IF NOT EXISTS bill_assignable BOOLEAN NOT NULL DEFAULT TRUE;

-- Accountants and main accountants don't do field delivery by default
UPDATE workers SET bill_assignable = FALSE WHERE worker_type IN ('ACCOUNTANT', 'MAIN_ACCOUNTANT');
