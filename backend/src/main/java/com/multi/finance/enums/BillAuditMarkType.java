package com.multi.finance.enums;

/** How a bill was resolved during a month-end reconciliation sweep. */
public enum BillAuditMarkType {
    /** Physically present in the file — nothing to do. */
    IN_HAND,
    /** Customer has paid but the payment was never entered in the system. */
    PAID_NOT_ENTERED,
    /** Not in the file and not known to be paid — needs follow-up. */
    MISSING
}
