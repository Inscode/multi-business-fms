-- V55 — Add normal_working_hours to workers.
--        Used by time-log monthly view to auto-fill hours when admin marks a day.
--        Defaults: DELIVERY/SHOP = 10h, ACCOUNTANT/MAIN_ACCOUNTANT = 8h, others = 8h.

ALTER TABLE workers ADD COLUMN normal_working_hours INT DEFAULT 8;

UPDATE workers SET normal_working_hours = 10 WHERE worker_type IN ('DELIVERY', 'SHOP');
UPDATE workers SET normal_working_hours = 8  WHERE worker_type IN ('ACCOUNTANT', 'MAIN_ACCOUNTANT');
-- All other types already have 8 from DEFAULT
