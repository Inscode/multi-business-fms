-- V54 — Scope bill_number uniqueness to business type.
--        Previously bill_number was globally unique across all businesses,
--        causing clashes when different business divisions use the same numbering.
--        Now MAN-250 can exist once per business (RAINCO, STATIONERY, etc.).

ALTER TABLE bills DROP CONSTRAINT uk_bills_bill_number;

ALTER TABLE bills ADD CONSTRAINT uk_bills_bill_number_business UNIQUE (bill_number, business);
