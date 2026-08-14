package com.multi.finance.enums;

public enum BusinessType {
    RAINCO,
    RETAIL_SHOP,
    PLASTIC,
    HARDWARE,
    STATIONERY,
    /**
     * Invoices from the invoicing module that carry more than one category on a single
     * document. A bill belongs to exactly one business, so mixed invoices land here
     * rather than being forced under whichever category happens to be largest.
     */
    MIX,
    DEMO
}
