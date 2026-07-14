-- Drop and recreate the bills business check constraint to include DEMO.
-- Hibernate auto-generated this constraint from the BusinessType enum at schema creation time;
-- adding DEMO to the enum requires updating the constraint manually.
ALTER TABLE bills DROP CONSTRAINT IF EXISTS bills_business_check;
ALTER TABLE bills ADD CONSTRAINT bills_business_check
    CHECK (business IN ('RAINCO','RETAIL_SHOP','STATIONERY','PLASTIC','HARDWARE','DEMO'));
