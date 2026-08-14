package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BillReturnItemResponse {
    private Long id;
    private Long productId;
    private String itemName;
    private BigDecimal unitPrice;
    private Integer quantityRequested;
    private Integer quantityReturned;
    private BigDecimal lineTotal;

    private Long invoiceLineId;
    private Long itemId;
    private String itemCode;

    /** WSP x qty, before discounts. */
    private BigDecimal grossValue;
    private BigDecimal slabDiscountPct;
    private BigDecimal cashDiscountPct;
    /** What the customer is credited, after both discounts. */
    private BigDecimal creditAmount;

    /** Someone typed over the calculation. */
    private Boolean amountEdited;
    /** What the calculation produced, so the override can be seen for what it is. */
    private BigDecimal computedCreditAmount;
}