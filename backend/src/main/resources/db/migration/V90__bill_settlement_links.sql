-- One system bill collected across several hand-written bills.
--
-- V87 let a system bill point at one manual bill, which covers the common case: the same
-- sale billed twice, once by hand and once here. But a rep out on a round writes a bill
-- at each shop and the system raises one covering the load, so the money for one system
-- bill comes in on three or four hand-written ones. A single pointer could name only the
-- first of them, leaving the rest looking unconnected to anything.
--
-- Distinct from bill_stock_links, which ties a system bill to many manual ones for
-- end-of-month stock reconciliation. That is about goods; this is about money.
CREATE TABLE IF NOT EXISTS bill_settlement_links (
    id             BIGSERIAL PRIMARY KEY,
    system_bill_id BIGINT NOT NULL REFERENCES bills(id) ON DELETE CASCADE,
    manual_bill_id BIGINT NOT NULL REFERENCES bills(id) ON DELETE CASCADE,
    note           VARCHAR(300),
    linked_by      VARCHAR(100),
    linked_at      TIMESTAMP NOT NULL DEFAULT now(),

    -- A hand-written bill collects for one system bill only. Without this the same
    -- money could be counted as settling two of them, and both would close on it.
    CONSTRAINT uk_settlement_manual UNIQUE (manual_bill_id),
    CONSTRAINT settlement_not_self CHECK (system_bill_id <> manual_bill_id)
);

CREATE INDEX IF NOT EXISTS idx_settlement_system ON bill_settlement_links (system_bill_id);

-- Everything already linked one-to-one becomes a link here, so the two never disagree.
INSERT INTO bill_settlement_links (system_bill_id, manual_bill_id, note, linked_by, linked_at)
SELECT b.id, b.settled_on_bill_id, b.settled_on_note, b.settled_on_by,
       COALESCE(b.settled_on_at, now())
  FROM bills b
 WHERE b.settled_on_bill_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM bill_settlement_links l
                    WHERE l.manual_bill_id = b.settled_on_bill_id);

-- bills.settled_on_bill_id stays, holding the first of the linked bills.
--
-- Kept on purpose rather than dropped: every place that decides what is outstanding,
-- what appears on the aging report and what can take a payment already reads it, and a
-- single column is one cheap check where a join would be a query. It means "this bill's
-- money is collected elsewhere"; the table says where, in full.
