INSERT INTO users (username, password_hash, full_name, role, active, created_at, updated_at)
VALUES ('mainaccountant', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LPVdSqKJ0pe',
        'Main Accountant', 'MAIN_ACCOUNTANT', true, NOW(), NOW());