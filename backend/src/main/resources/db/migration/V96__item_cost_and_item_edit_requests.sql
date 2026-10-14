ALTER TABLE inv_items
    ADD COLUMN cost_price NUMERIC(12, 2);

ALTER TABLE edit_requests
    DROP CONSTRAINT IF EXISTS edit_requests_type_check;

ALTER TABLE edit_requests
    ADD CONSTRAINT edit_requests_type_check
        CHECK (type IN ('BILL', 'PAYMENT', 'CUSTOMER', 'ITEM'));