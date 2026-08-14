-- Let a cancelled bill's number be used again.
--
-- A bill entered wrongly and cancelled still held its number for good, because the
-- uniqueness rule counted every row regardless of status. The number is a physical
-- thing — a page in a book, a slot in the agent's sequence — so it has to go back into
-- the run once the bill that took it is void. Otherwise the sequence shows a permanent
-- gap that nobody can fill and the "not entered" check keeps reporting.
--
-- Enforced with a partial unique index rather than a plain constraint: cancelled rows
-- are simply not part of the uniqueness set. Postgres still guarantees that no two
-- *live* bills in a business can share a number, which is the rule that actually
-- matters, and it does so at the database rather than on trust.
ALTER TABLE bills DROP CONSTRAINT IF EXISTS uk_bills_bill_number_business;

DROP INDEX IF EXISTS uk_bills_bill_number_business_active;
CREATE UNIQUE INDEX uk_bills_bill_number_business_active
    ON bills (bill_number, business)
    WHERE status <> 'CANCELLED';
