package com.multi.finance.enums;

public enum CollectionNoteStatus {
    PENDING,  // owner marked, accountant hasn't entered payment yet
    MATCHED   // accountant entered a payment linked to this note
}