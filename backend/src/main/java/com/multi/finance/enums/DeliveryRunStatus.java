package com.multi.finance.enums;

/** Where a lorry round has got to. */
public enum DeliveryRunStatus {
    /** Bills are still being entered into it. At most one open run per area and date. */
    OPEN,
    /** The lorry has left; the bills on it are fixed. */
    DISPATCHED,
    /** Back, and every drop accounted for. */
    COMPLETED,
    /** Called off. Its bills keep their mode but lose the run. */
    CANCELLED
}
