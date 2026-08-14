package com.multi.finance.enums;

public enum BillSource {
    MANUAL,
    SYSTEM,
    DRAFT,
    MANUAL_BOOK,
    /**
     * Raised automatically from an invoice in the invoicing module, so payments can be
     * collected against it. Deliberately distinct from SYSTEM: the stock workflows key
     * off SYSTEM, and invoicing has already moved the stock for these.
     */
    INVOICE
}