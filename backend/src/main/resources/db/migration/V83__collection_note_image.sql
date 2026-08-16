-- A photograph against a marked collection.
--
-- Collections are marked on their own screen, which creates a collection note rather
-- than a payment directly. The note is what later becomes the payment, so the evidence
-- has to be attached here — asking for it only at payment entry would miss every
-- collection that came in through this route.
--
-- Optional, unlike the accountant's on a payment: this screen is used by admins and
-- owners, who are usually holding the paperwork already.
ALTER TABLE collection_notes
    ADD COLUMN IF NOT EXISTS receipt_image_url   TEXT,
    ADD COLUMN IF NOT EXISTS receipt_uploaded_at TIMESTAMP;
