package com.multi.finance.invoicing.dto.response;

import com.multi.finance.invoicing.enums.CategoryType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** An import run, with the product totals to check against the agent's summary bill. */
@Data
@Builder
public class ImportBatchResponse {
    private Long id;
    private CategoryType category;
    private String fileName;
    private String importedBy;
    private LocalDateTime importedAt;
    private Integer invoiceCount;

    /** The invoices on this run — each can be excluded from the totals below. */
    private List<BatchInvoice> invoices;

    /** Product totals across the included invoices. */
    private List<ProductLine> products;

    private Integer totalQty;
    private Integer totalFreeQty;
    private BigDecimal totalValue;

    @Data
    @Builder
    public static class BatchInvoice {
        private Long id;
        private String invoiceNo;
        private String externalRef;
        private String customerName;
        private BigDecimal netTotal;
        private boolean included;
    }

    @Data
    @Builder
    public static class ProductLine {
        private Long itemId;
        private String itemCode;
        private String description;
        private String brandName;
        private Integer qty;
        private Integer freeQty;
        private BigDecimal value;
    }
}
