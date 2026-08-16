-- A cancelled bill keeps its number. Reversing V79.
--
-- V79 freed the number of a cancelled bill so the run had no permanent gap. In use
-- that turns out to be the wrong trade: the same number can then exist twice in one
-- business, and anyone looking at SYS-768 has to work out which of the two they mean.
-- A cancelled bill is still a record of something that happened, and its number is
-- part of that record.
--
-- The gap-free run is served by deletion instead: a bill entered in error is deleted
-- outright, which releases its number because there is no longer anything holding it.
-- Cancel keeps the history; delete removes it. Those are different intentions and now
-- have different effects.
DROP INDEX IF EXISTS uk_bills_bill_number_business_active;

-- Restore the plain rule: one bill per number per business, whatever its status.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_bills_bill_number_business'
    ) THEN
        -- If V79 ran and a number was reused, two rows now share one. The constraint
        -- cannot be created over that, and guessing which to keep is not the database's
        -- decision to make — so it fails loudly with the pair named.
        ALTER TABLE bills
            ADD CONSTRAINT uk_bills_bill_number_business
            UNIQUE (bill_number, business);
    END IF;
END $$;
