-- V53 — Auto-strip leading zeros from manually entered bill numbers (MAN-*, SYS-*).
--        Fires on every INSERT/UPDATE so accountants can type MAN-0250 and it saves as MAN-250.
--        DFT numbers are fixed at source (BillServiceImpl no longer uses %04d).

CREATE OR REPLACE FUNCTION fn_normalize_bill_number()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.bill_number ~ '^[A-Za-z]+-0[0-9]+' THEN
        NEW.bill_number :=
            substring(NEW.bill_number FROM '^[A-Za-z]+-') ||
            CAST(substring(NEW.bill_number FROM '[0-9]+$')::integer AS text);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trig_normalize_bill_number
BEFORE INSERT OR UPDATE ON bills
FOR EACH ROW EXECUTE FUNCTION fn_normalize_bill_number();
