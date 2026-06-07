package com.multi.finance.enums;

public enum WorkerPaymentStatus {
    PENDING,          // worker entered, awaiting owner confirmation
    OWNER_CONFIRMED,  // owner confirmed → CollectionNote created for accountant
    REJECTED          // owner rejected
}
