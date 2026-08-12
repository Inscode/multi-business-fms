-- ============================================================
-- V65: Approval step for damage dispatches (send to company).
-- Accountants can now prepare a dispatch, but damage stock is only
-- deducted when an admin approves it — previously creation both
-- required admin and deducted immediately.
--
-- Existing rows are backfilled as APPROVED: their stock movements
-- were already written under the old behaviour.
-- ============================================================

ALTER TABLE damage_dispatches ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE damage_dispatches ADD COLUMN rejection_reason TEXT;
ALTER TABLE damage_dispatches ADD COLUMN reviewed_by BIGINT REFERENCES users(id);
ALTER TABLE damage_dispatches ADD COLUMN reviewed_at TIMESTAMP;

UPDATE damage_dispatches
SET status      = 'APPROVED',
    reviewed_by = entered_by,
    reviewed_at = created_at;

CREATE INDEX idx_damage_dispatches_status ON damage_dispatches(status);
