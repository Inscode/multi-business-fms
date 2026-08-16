package com.multi.finance.enums;

/** How the goods on a bill reached the customer. */
public enum DeliveryMode {
    /** Bills entered before deliveries were recorded. Never guessed into another mode. */
    UNSPECIFIED,
    /** Went out on a lorry round to an area, alongside other bills for that area. */
    ROUTE,
    /** Sent out on its own, outside any round. */
    IMMEDIATE,
    /** The customer collected it from the store. */
    STORE_PICKUP
}
