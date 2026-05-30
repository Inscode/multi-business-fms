package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class StockBillResponse {

    private Long id;
    private String billNumber;
    private String billSource;
    private String customerName;
    private BigDecimal amount;
    private String paymentType;
    private LocalDate billDate;
    private String enteredByName;
    private Long totalQty;
    /** Per-product items — populated for linking/status endpoints, null otherwise */
    private List<BillStockItemResponse> items;
    /** True if this is a SYSTEM bill with linked DRAFT/MANUAL children — items are reference-only, no stock movement */
    private Boolean isLinkingBill;
}
