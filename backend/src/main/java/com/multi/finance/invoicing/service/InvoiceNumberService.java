package com.multi.finance.invoicing.service;

import com.multi.finance.enums.BillSource;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.invoicing.enums.InvoiceMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Numbers invoices exactly the way the bills section numbers bills, so one string
 * identifies the same document in both places and the prefix says where it came from.
 *
 *   SYS-13271   system bill
 *   MAN-324     Rainco manual book
 *   BK-88       shared physical book (Plastic / Stationery manual, Rainco manual book)
 *
 * Numbers are unique per business, not globally — SYS-743 is a legitimate Rainco bill
 * and a legitimate Stationery bill at the same time.
 */
@Service
@RequiredArgsConstructor
public class InvoiceNumberService {

    /**
     * Below this, an imported Rainco reference is a manual book bill rather than a
     * system one. Rainco is the only business where both arrive through the import.
     */
    private static final int RAINCO_MANUAL_CEILING = 10_000;

    /** A bill belongs to one business; mixed-category invoices get their own. */
    public BusinessType businessFor(InvoiceMethod method) {
        return switch (method) {
            case RAINCO_ONLY     -> BusinessType.RAINCO;
            case STATIONERY_ONLY -> BusinessType.STATIONERY;
            case PLASTIC_ONLY    -> BusinessType.PLASTIC;
            case MIX             -> BusinessType.MIX;
        };
    }

    /**
     * Which kind of bill an imported reference represents. Rainco's book numbers run
     * below {@value #RAINCO_MANUAL_CEILING}; everything above that, and everything from
     * Stationery, is a system bill.
     */
    public BillSource sourceForImport(BusinessType business, String externalRef) {
        if (business == BusinessType.RAINCO) {
            Integer digits = numericPart(externalRef);
            if (digits != null && digits < RAINCO_MANUAL_CEILING) return BillSource.MANUAL;
        }
        return BillSource.SYSTEM;
    }

    /**
     * The number both the invoice and its bill carry.
     *
     * @param number the bare number — picked from the suggestion dropdown when typed,
     *               or taken from the agent reference when imported
     */
    public String format(BusinessType business, BillSource source, String number) {
        String n = stripLeadingZeros(number);
        if (n == null || n.isBlank()) {
            throw new IllegalArgumentException("A bill number is required");
        }
        return prefixFor(business, source) + n;
    }

    /** Mirrors the prefix rules in the bills section exactly — see BillServiceImpl.createBill. */
    private String prefixFor(BusinessType business, BillSource source) {
        return switch (source) {
            case SYSTEM -> "SYS-";
            case MANUAL_BOOK -> "BK-";
            // Plastic and Stationery manual bills come out of the shared physical book.
            case MANUAL -> (business == BusinessType.PLASTIC || business == BusinessType.STATIONERY)
                    ? "BK-" : "MAN-";
            case DRAFT, INVOICE -> throw new IllegalArgumentException(
                    "An invoice must be a system or manual bill, not " + source);
        };
    }

    /**
     * The digits in an agent reference. "BDSL/13271" and "13271" both give 13271, so a
     * reference reads the same whatever the agent prefixed it with.
     */
    public String digitsOf(String ref) {
        if (ref == null) return null;
        String digits = ref.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : stripLeadingZeros(digits);
    }

    private Integer numericPart(String ref) {
        String digits = digitsOf(ref);
        if (digits == null) return null;
        try { return Integer.parseInt(digits); } catch (NumberFormatException e) { return null; }
    }

    private String stripLeadingZeros(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        try { return String.valueOf(Long.parseLong(t)); } catch (NumberFormatException e) { return t; }
    }
}
