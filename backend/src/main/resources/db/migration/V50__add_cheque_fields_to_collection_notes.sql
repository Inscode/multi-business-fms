ALTER TABLE collection_notes ADD COLUMN IF NOT EXISTS cheque_number  VARCHAR(100);
ALTER TABLE collection_notes ADD COLUMN IF NOT EXISTS bank_name      VARCHAR(100);
ALTER TABLE collection_notes ADD COLUMN IF NOT EXISTS branch_name    VARCHAR(100);
ALTER TABLE collection_notes ADD COLUMN IF NOT EXISTS cheque_date    DATE;
ALTER TABLE collection_notes ADD COLUMN IF NOT EXISTS source_entry_id BIGINT;
