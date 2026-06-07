package com.multi.finance.enums;

public enum WorkerVisitStatus {
    NOT_VISITED,
    SHOP_CLOSED,
    REVISIT_REQUESTED,   // worker left a reason note (e.g. "come next Friday")
    DELIVERED_PENDING,   // goods delivered, payment not collected yet
    DELIVERED_PAID       // goods delivered and payment collected
}
