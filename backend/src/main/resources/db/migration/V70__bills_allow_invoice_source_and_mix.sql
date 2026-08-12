-- Bills raised from the invoicing module use BillSource.INVOICE, and mixed-category
-- invoices use BusinessType.MIX. Both columns carry CHECK constraints that Hibernate
-- generated from the enums at schema creation time and that no migration has kept in
-- step since — so a new enum value is accepted by the application and then rejected by
-- the database, which surfaces as an opaque constraint violation on save.

ALTER TABLE bills DROP CONSTRAINT IF EXISTS bills_business_check;
ALTER TABLE bills ADD CONSTRAINT bills_business_check
    CHECK (business IN ('RAINCO','RETAIL_SHOP','STATIONERY','PLASTIC','HARDWARE','MIX','DEMO'));

ALTER TABLE bills DROP CONSTRAINT IF EXISTS bills_bill_source_check;
ALTER TABLE bills ADD CONSTRAINT bills_bill_source_check
    CHECK (bill_source IN ('MANUAL','SYSTEM','DRAFT','MANUAL_BOOK','INVOICE'));
