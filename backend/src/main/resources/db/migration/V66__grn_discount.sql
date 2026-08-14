-- ============================================================
-- V66: Supplier discount on goods received notes.
-- The principal's GRN document prices each line at gross, then applies a
-- flat category discount to reach the net value. The rate is snapshotted
-- onto the note at creation so a later rate change never rewrites history.
-- ============================================================

ALTER TABLE inv_grns ADD COLUMN discount_pct NUMERIC(5,2) NOT NULL DEFAULT 0;

-- Current rates, editable without a deploy
INSERT INTO inv_settings (setting_key, value) VALUES
    ('grn_discount_pct_RAINCO',     '8.54'),
    ('grn_discount_pct_STATIONERY', '7'),
    ('grn_discount_pct_PLASTIC',    '0');

-- Existing notes pre-date the column but their supplier documents carried the
-- same rates, so backfill by category rather than leaving them at zero.
UPDATE inv_grns SET discount_pct = 8.54 WHERE category = 'RAINCO';
UPDATE inv_grns SET discount_pct = 7    WHERE category = 'STATIONERY';
