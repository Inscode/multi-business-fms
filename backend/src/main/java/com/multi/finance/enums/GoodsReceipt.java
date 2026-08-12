package com.multi.finance.enums;

/** What the accountant actually found in the box when the return came back. */
public enum GoodsReceipt {
    /** Everything claimed arrived. */
    ALL,
    /** Some of it arrived; the per-line received quantities carry the detail. */
    PARTIAL,
    /** Nothing arrived. */
    NONE
}
