-- A photograph of the bill against every payment.
--
-- An accountant entering a collection in the field is recording money that has already
-- changed hands; the photograph is the only evidence tying the figure to the paper the
-- customer signed. It is required of them for that reason.
--
-- The admin's own image is separate and optional: an admin entering a collection is
-- usually holding the paperwork already, and confirming someone else's payment is a
-- check rather than a claim. Kept in its own column so the two are never confused —
-- one is what the accountant saw, the other is what the admin saw.
--
-- Only the URL is stored. The file itself lives in ImageKit, the same as task images.
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS receipt_image_url      TEXT,
    ADD COLUMN IF NOT EXISTS confirm_image_url      TEXT,
    ADD COLUMN IF NOT EXISTS receipt_uploaded_at    TIMESTAMP,
    ADD COLUMN IF NOT EXISTS confirm_uploaded_at    TIMESTAMP;
