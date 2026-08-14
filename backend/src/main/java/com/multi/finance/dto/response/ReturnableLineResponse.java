package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * One line of the invoice behind a bill, offered up for return.
 *
 * <p>The prices and the discount are the ones actually charged, read off the stored
 * invoice line rather than looked up fresh — item master prices move, and a return
 * credited at today's price would not reverse what the customer paid.
 */
@Data
@Builder
public class ReturnableLineResponse {
    private Long invoiceLineId;
    private Long itemId;
    private String itemCode;
    private String description;
    private String brandName;

    /** What was sold on this line. */
    private Integer qtySold;
    /** Already claimed back on earlier returns. */
    private Integer qtyAlreadyReturned;
    /** qtySold - qtyAlreadyReturned; the most this line can still give back. */
    private Integer qtyAvailable;

    private BigDecimal wsp;
    /** The slab % charged on this line at the time of sale. */
    private BigDecimal appliedDiscountPct;
    /** The bill's cash discount %, present only when it was a cash sale. */
    private BigDecimal cashDiscountPct;
}
