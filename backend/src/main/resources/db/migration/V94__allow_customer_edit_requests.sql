ALTER TABLE edit_requests
    DROP CONSTRAINT IF EXISTS edit_requests_type_check;

ALTER TABLE edit_requests
    ADD CONSTRAINT edit_requests_type_check
        CHECK (type IN ('BILL', 'PAYMENT', 'CUSTOMER'));
