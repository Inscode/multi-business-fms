package com.multi.finance.invoicing.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InvoiceLineResponse {
    private Long id;
    private Long itemId;
    private String itemCode;
    private String itemDescription;
    private String brandName;
    private Long brandId;
    private Integer qty;
    /** Given free — no value, but deducted from stock. */
    private Integer freeQty;
    private BigDecimal mrp;
    private BigDecimal marginPct;
    private BigDecimal wsp;
    private BigDecimal value;
    private BigDecimal appliedDiscountPct;
    private Integer sortOrder;
}
