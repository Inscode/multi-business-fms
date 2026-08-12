package com.multi.finance.invoicing.enums;

/** How an invoice got into the system — shown to the admin on review. */
public enum InvoiceSource {
    /** Keyed in on the invoice form. */
    MANUAL,
    /** Loaded from a Ventura XLS export. */
    IMPORT
}
