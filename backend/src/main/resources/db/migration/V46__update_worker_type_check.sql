-- Expand worker_type check constraint to include new staff designations
ALTER TABLE workers DROP CONSTRAINT IF EXISTS workers_worker_type_check;

ALTER TABLE workers ADD CONSTRAINT workers_worker_type_check
    CHECK (worker_type IN ('DELIVERY', 'SALES_REP', 'SHOP', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'DRIVER', 'OTHER'));
