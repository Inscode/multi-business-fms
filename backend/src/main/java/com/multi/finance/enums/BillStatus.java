package com.multi.finance.enums;

public enum BillStatus {
    CREATED,
    ASSIGNED,
    SHOP_WORKER_ASSIGNED,
    SHOP_RECEIVED,
    STORE_RECEIVED,
    // Fully paid, but at least one payment is still awaiting admin confirmation
    AWAITING_CONFIRMATION,
    COMPLETED,
    CANCELLED
}
