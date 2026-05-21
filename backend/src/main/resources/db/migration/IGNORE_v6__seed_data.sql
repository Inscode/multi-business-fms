ALTER TABLE bills
RENAME COLUMN worker_id TO holder_id;
-- Users (passwords are BCrypt of the plain text shown in comments)
INSERT INTO users (username, password_hash, full_name, role, active, created_at, updated_at) VALUES
('admin1',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LPVdSqKJ0pe', 'Admin User',       'ADMIN',          true, NOW(), NOW()),
('owner',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LPVdSqKJ0pe', 'Owner User',       'OWNER',          true, NOW(), NOW()),
('accountant','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LPVdSqKJ0pe', 'Accountant Main',  'ACCOUNTANT',     true, NOW(), NOW()),
('shopstaff', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LPVdSqKJ0pe', 'Shop Accountant',  'SHOP_ACCOUNTANT', true, NOW(), NOW());

-- Workers
INSERT INTO workers (full_name, worker_type, base_salary, active, joined_date, created_at, updated_at) VALUES
('Worker One',   'DELIVERY',  35000.00, true, '2023-01-01', NOW(), NOW()),
('Worker Two',   'DELIVERY',  35000.00, true, '2023-03-15', NOW(), NOW()),
('Worker Three', 'SALES_REP', 40000.00, true, '2022-06-01', NOW(), NOW()),
('Worker Four',  'SALES_REP', 40000.00, true, '2023-07-10', NOW(), NOW()),
('Worker Five',  'SHOP',      30000.00, true, '2024-01-01', NOW(), NOW());

-- Bills
INSERT INTO bills (bill_number, business, division, bill_source, bill_type, customer_name, total_amount, amount_paid, balance_remaining, fully_paid, status, entered_by, bill_date, created_at, updated_at) VALUES
('BILL-1001', 'RAINCO',     'STORE', 'MANUAL', 'CREDIT', 'Customer Alpha',   50000.00, 0.00, 50000.00, false, 'CREATED',  3, NOW(), NOW(), NOW()),
('BILL-1002', 'RAINCO',     'STORE', 'MANUAL', 'CREDIT', 'Customer Beta',    25000.00, 0.00, 25000.00, false, 'ASSIGNED', 3, NOW(), NOW(), NOW()),
('BILL-1003', 'HARDWARE',   'STORE', 'MANUAL', 'CASH',   'Customer Gamma',   12000.00, 0.00, 12000.00, false, 'CREATED',  3, NOW(), NOW(), NOW()),
('BILL-1004', 'STATIONERY', 'STORE', 'MANUAL', 'CREDIT', 'Customer Delta',   78000.00, 0.00, 78000.00, false, 'ASSIGNED', 3, NOW(), NOW(), NOW()),
('BILL-1005', 'RAINCO',     'SHOP',  'MANUAL', 'CREDIT', 'Customer Epsilon', 45000.00, 0.00, 45000.00, false, 'SHOP_WORKER_ASSIGNED', 3, NOW(), NOW(), NOW());

-- Assign workers to bills
UPDATE bills SET current_holder_id = 1, bill_date = CURRENT_DATE WHERE bill_number = 'BILL-1002';
UPDATE bills SET current_holder_id = 3, bill_date = CURRENT_DATE WHERE bill_number = 'BILL-1004';
UPDATE bills SET current_holder_id = 5, bill_date = CURRENT_DATE WHERE bill_number = 'BILL-1005';
UPDATE bills SET bill_date = CURRENT_DATE WHERE bill_number IN ('BILL-1001', 'BILL-1003');

-- Payments
INSERT INTO payments (bill_id, amount, payment_type, status, entered_by, payment_date, is_partial, created_at, updated_at) VALUES
(2, 25000.00, 'CASH',   'ENTERED',   3, CURRENT_DATE, false, NOW(), NOW()),
(4, 30000.00, 'CHEQUE', 'ENTERED',   3, CURRENT_DATE, true,  NOW(), NOW()),
(4, 48000.00, 'CASH',   'CONFIRMED', 3, CURRENT_DATE, false, NOW(), NOW());