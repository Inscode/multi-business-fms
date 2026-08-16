-- Photographs behind a return, and the month a lorry round belongs to.
--
-- Returns are evidenced two different ways, because they are counted two different
-- ways:
--
--   * A store pickup or an immediate delivery is one shop. Its return is photographed
--     on its own, and the photo belongs to that return.
--
--   * A route round is checked the morning after the lorry is back, and the returns
--     from fifteen shops are written down the same page of a book. That page is
--     evidence for every return on the round at once, so it belongs to the run — and
--     there is rarely just one page.
--
-- One table serves both: exactly one owner is set per row. A return on a route shows
-- its run's pages; a return on a pickup shows its own photo.
CREATE TABLE IF NOT EXISTS return_images (
    id              BIGSERIAL PRIMARY KEY,

    -- Exactly one of these two.
    bill_return_id  BIGINT REFERENCES bill_returns(id) ON DELETE CASCADE,
    delivery_run_id BIGINT REFERENCES delivery_runs(id) ON DELETE CASCADE,

    -- Damage and salable are written in separate books, and are reviewed and settled
    -- separately, so a page is always one or the other.
    return_type     VARCHAR(20) NOT NULL,

    /* "page 1", "page 2" — the accountant photographs the book as they fill it. */
    page_no         INT,

    image_url       TEXT        NOT NULL,
    uploaded_by     VARCHAR(100),
    uploaded_at     TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT return_images_type_check
        CHECK (return_type IN ('DAMAGE','SALABLE')),

    -- A photo hanging off nothing, or off both, could never be found again.
    CONSTRAINT return_images_one_owner_check
        CHECK ((bill_return_id IS NOT NULL) <> (delivery_run_id IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_return_images_return ON return_images (bill_return_id);
CREATE INDEX IF NOT EXISTS idx_return_images_run
    ON return_images (delivery_run_id, return_type);

-- ── The month a round belongs to ─────────────────────────────────────────────
-- A round planned for the end of one month often goes out at the start of the next.
-- The date it left and the month it counts against are different facts, and reporting
-- wants the second — so it is stored rather than derived from the date.
ALTER TABLE delivery_runs
    ADD COLUMN IF NOT EXISTS run_month DATE;

-- Existing runs count against the month they were planned in, which is the best
-- available answer and matches what would have been assumed anyway.
UPDATE delivery_runs
   SET run_month = date_trunc('month', planned_date)::date
 WHERE run_month IS NULL;

CREATE INDEX IF NOT EXISTS idx_delivery_runs_month ON delivery_runs (run_month DESC);
