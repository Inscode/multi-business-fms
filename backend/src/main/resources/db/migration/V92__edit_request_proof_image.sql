-- Evidence for an edit request that changes an amount.
--
-- Most edits are corrections to how a bill reads — a misspelled shop, the wrong area,
-- a date a day out — and the reason typed alongside is enough to judge them by. An
-- amount is different: it changes what the customer owes, and the admin approving it
-- is being asked to take somebody's word for a figure they cannot see.
--
-- So a photograph of the bill is required when, and only when, the amount moves. Asking
-- for one on every edit would attach a picture to a corrected spelling, and a rule that
-- fires on everything is one people learn to satisfy without reading.
ALTER TABLE edit_requests
    ADD COLUMN IF NOT EXISTS proof_image_url  TEXT,
    ADD COLUMN IF NOT EXISTS proof_uploaded_at TIMESTAMP;
