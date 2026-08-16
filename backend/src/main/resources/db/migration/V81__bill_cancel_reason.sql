-- Why a bill was cancelled, and by whom.
--
-- A cancellation used to leave nothing but a status: the bill stopped counting and no
-- record said why. That mattered little while a cancelled number stayed spent forever,
-- but V79 hands the number back to the run, so the same number can appear twice — once
-- void, once live. Without a reason on the void one, the pair is impossible to explain
-- months later.
ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(300),
    ADD COLUMN IF NOT EXISTS cancelled_by  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS cancelled_at  TIMESTAMP;
