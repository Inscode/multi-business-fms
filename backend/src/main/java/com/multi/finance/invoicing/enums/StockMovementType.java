package com.multi.finance.invoicing.enums;

public enum StockMovementType {
    INVOICE_DEDUCTION,   // stock reduced when invoice is confirmed
    RETURN_DAMAGE,       // damage return — stock written off
    RETURN_SALABLE,      // salable return — stock returned to shelf
    MANUAL_ADJUSTMENT,
    GRN_RECEIPT          // stock added when a Goods Received Note is approved
}
