-- A damage bucket on invoicing items.
--
-- Damaged goods coming back are real, countable stock: they sit in the warehouse until
-- they are dispatched to the agent and claimed. But they must never be sellable, so
-- they cannot go back into stock_qty.
--
-- This mirrors what the legacy shadow ledger already does with DAMAGE_IN minus
-- DAMAGE_TO_COMPANY, giving the invoicing ledger the same notion of damage on hand.
--
-- Note the goods already left stock_qty when the invoice was raised. A damage return
-- therefore ADDS to damage_qty and leaves stock_qty alone — it is a move between
-- buckets, not a second deduction.
ALTER TABLE inv_items
    ADD COLUMN IF NOT EXISTS damage_qty INTEGER NOT NULL DEFAULT 0;
