-- Keeping a bill out of the aging report.
--
-- Some outstanding bills are not chaseable debt — a written-off balance, a rounding
-- remnant, an internal transfer — and leaving them in makes the report overstate what
-- is actually collectable, which is worse than useless when it is the number people
-- act on.
--
-- Hiding is deliberate and attributable rather than a silent delete: the balance is
-- still owed and still on the bill, it is only kept off this one report. Who hid it and
-- why are recorded so an excluded bill can always be explained and put back.
ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS excluded_from_aging   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS aging_exclusion_reason VARCHAR(300),
    ADD COLUMN IF NOT EXISTS aging_excluded_by     VARCHAR(100),
    ADD COLUMN IF NOT EXISTS aging_excluded_at     TIMESTAMP;

-- The report filters on this every time it runs, and only a handful of rows will ever
-- be true, so the index is partial.
CREATE INDEX IF NOT EXISTS idx_bills_excluded_from_aging
    ON bills (business) WHERE excluded_from_aging = TRUE;
