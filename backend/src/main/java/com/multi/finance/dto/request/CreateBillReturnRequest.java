package com.multi.finance.dto.request;

import com.multi.finance.enums.ReturnType;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateBillReturnRequest {
    private ReturnType returnType;

    /**
     * True when the items were picked off this bill's own invoice lines. The credit is
     * then computed from what was actually charged, and quantities are capped at what
     * was sold.
     */
    private Boolean fromSameBill;

    /**
     * Whether the goods were sold for cash, so the cash discount comes off the credit.
     * Only read for a different-bill return — for a same-bill return the answer is
     * already on the bill and is taken from there.
     */
    private Boolean cashSale;
    private List<BillReturnItemRequest> items;
    private BigDecimal discountPercentage;
    private BigDecimal discountFixed;
    private BigDecimal predictedValue;
    private Long responsibleWorkerId;
    private String notes;
}