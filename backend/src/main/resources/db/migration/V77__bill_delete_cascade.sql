-- Deleting a bill fails on whichever child table happens to have a row.
--
-- Roughly fifteen tables carry a bill_id, and the delete service only cleared two of
-- them (payments and returns). Every other one — bill_reviews, audit marks, reminders,
-- backorders, stock links, worker visits — aborts the delete with a foreign key error
-- naming a table the accountant has never heard of.
--
-- Clearing them one by one in Java means enumerating every table and re-enumerating it
-- whenever a new one is added, which is exactly how this broke. The rule belongs where
-- the relationship is declared: a row that hangs off a bill has no meaning without it,
-- so it goes when the bill goes.
--
-- This rewrites every foreign key pointing at bills to ON DELETE CASCADE, found by
-- looking them up rather than by listing names, so nothing can be missed and anything
-- added later only needs the same treatment once.
--
-- What must NOT simply vanish — confirmed payments, worker collections, collection
-- notes — is refused in BillServiceImpl.deleteBill before it ever gets here. The
-- cascade is for the bill's own paperwork, not for other people's money.
DO $$
DECLARE
    fk RECORD;
BEGIN
    FOR fk IN
        SELECT con.conname   AS constraint_name,
               rel.relname   AS child_table,
               att.attname   AS child_column
          FROM pg_constraint con
          JOIN pg_class      rel ON rel.oid = con.conrelid
          JOIN pg_class      ref ON ref.oid = con.confrelid
          JOIN pg_attribute  att ON att.attrelid = con.conrelid
                                AND att.attnum = con.conkey[1]
         WHERE con.contype = 'f'
           AND ref.relname = 'bills'
           AND con.confdeltype <> 'c'          -- not already cascading
           AND array_length(con.conkey, 1) = 1 -- single-column keys only
    LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I',
                       fk.child_table, fk.constraint_name);
        EXECUTE format(
            'ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (%I) '
            'REFERENCES bills(id) ON DELETE CASCADE',
            fk.child_table, fk.constraint_name, fk.child_column);
        RAISE NOTICE 'bill delete now cascades: %.%', fk.child_table, fk.child_column;
    END LOOP;
END $$;
