package com.multi.finance.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BillReturnItemRequest {
    private Long productId;
    private String itemName;
    private BigDecimal unitPrice;
    private Integer quantityRequested;
    private Integer quantityReturned;

    /** Set when returning against this bill's own invoice line. */
    private Long invoiceLineId;
    /** The invoicing item, so stock can be moved. */
    private Long itemId;

    /**
     * The line's discount %. Ignored for a same-bill return, where it is read off the
     * original invoice line instead of trusted from the client.
     */
    private BigDecimal slabDiscountPct;

    /**
     * A credit typed over the computed one. Left null to accept the calculation; when
     * set, the computed figure is kept alongside and the edit is flagged to the admin.
     */
    private BigDecimal creditAmountOverride;
}