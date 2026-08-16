-- Lets a bill be AWAITING_CONFIRMATION.
--
-- bills_status_check was never written by hand — Hibernate generated it when it created
-- the column, listing the BillStatus values that existed at that moment. AWAITING_
-- CONFIRMATION was added to the enum afterwards, and a generated constraint is not
-- regenerated, so the database went on rejecting a status the application had been using
-- for months.
--
-- It surfaces on the last payment against a bill: paying it off in full while any one
-- payment is still unconfirmed moves it to AWAITING_CONFIRMATION, and the update fails
-- there rather than anywhere the status is set.
--
-- Written out in full rather than patched so the constraint now says what the enum says,
-- and stays a migration's business rather than a side effect of whichever schema tool
-- ran last.
DO $$
DECLARE
    c text;
BEGIN
    -- Any generated name, not only the conventional one — the constraint has been made
    -- by tooling, so its name cannot be assumed.
    FOR c IN
        SELECT con.conname
          FROM pg_constraint con
          JOIN pg_class rel ON rel.oid = con.conrelid
         WHERE rel.relname = 'bills'
           AND con.contype = 'c'
           AND pg_get_constraintdef(con.oid) ILIKE '%status%'
           AND pg_get_constraintdef(con.oid) ILIKE '%CREATED%'
    LOOP
        EXECUTE format('ALTER TABLE bills DROP CONSTRAINT %I', c);
    END LOOP;
END $$;

ALTER TABLE bills ADD CONSTRAINT bills_status_check
    CHECK (status IN ('CREATED',
                      'ASSIGNED',
                      'SHOP_WORKER_ASSIGNED',
                      'SHOP_RECEIVED',
                      'STORE_RECEIVED',
                      'AWAITING_CONFIRMATION',
                      'COMPLETED',
                      'CANCELLED'));

-- bill_source has the same history: MANUAL_BOOK and INVOICE were both added to the enum
-- after the column existed. Rewritten here rather than waiting for the day someone
-- happens to save a bill it does not allow.
DO $$
DECLARE
    c text;
BEGIN
    FOR c IN
        SELECT con.conname
          FROM pg_constraint con
          JOIN pg_class rel ON rel.oid = con.conrelid
         WHERE rel.relname = 'bills'
           AND con.contype = 'c'
           AND pg_get_constraintdef(con.oid) ILIKE '%bill_source%'
    LOOP
        EXECUTE format('ALTER TABLE bills DROP CONSTRAINT %I', c);
    END LOOP;
END $$;

ALTER TABLE bills ADD CONSTRAINT bills_bill_source_check
    CHECK (bill_source IN ('MANUAL','SYSTEM','DRAFT','MANUAL_BOOK','INVOICE'));
