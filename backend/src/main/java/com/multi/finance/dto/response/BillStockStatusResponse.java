package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BillStockStatusResponse {

    private Long billId;
    private String billNumber;
    private String billSource;
    private String reductionStatus;

    // Reconciliation fields (SYSTEM linking bills only)
    private Boolean stockReconciled;
    private Boolean quantitiesMatch;
    private java.math.BigDecimal childrenTotalAmount;
    private java.math.BigDecimal savingsAmount;

    /** Stock items attached directly to this bill (INDIVIDUALLY_REDUCED or legacy) */
    private List<BillStockItemResponse> ownItems;

    // ── Summary load context (SYSTEM bill inside a summary) ──────────────────
    private Long summaryLoadId;
    private String summaryStatus;
    private String summaryCreatedByName;
    private LocalDate summaryLoadDate;
    private List<SummaryItemInfo> summaryItems;
    private List<SiblingBillInfo> summaryRelatedBills;

    // ── Linked children (SYSTEM bill with DRAFT/MANUAL children) ─────────────
    private List<LinkedChildInfo> linkedChildren;

    /**
     * Per-product comparison: this SYSTEM bill's own reference items vs the SUM of all linked children.
     * Populated when this is a SYSTEM linking bill (has linked DRAFT/MANUAL children).
     * systemQty = reference items entered on the SYSTEM bill (0 if none yet)
     * childQty  = aggregated items from all linked children
     */
    private List<ItemComparisonRow> childrenAggregateComparison;

    // ── Linked parent (DRAFT/MANUAL linked to a SYSTEM parent) ───────────────
    private LinkedParentInfo linkedParent;

    @Data
    @Builder
    public static class SummaryItemInfo {
        private Long productId;
        private String productName;
        private Long quantity;
    }

    @Data
    @Builder
    public static class SiblingBillInfo {
        private Long billId;
        private String billNumber;
        private String customerName;
        private BigDecimal amount;
        private Long totalQty;
    }

    @Data
    @Builder
    public static class LinkedChildInfo {
        private Long billId;
        private String billNumber;
        private String billSource;
        private String customerName;
        private BigDecimal amount;
        private List<BillStockItemResponse> items;
        private String linkedByName;
        private LocalDateTime linkedAt;
        private String notes;
    }

    @Data
    @Builder
    public static class LinkedParentInfo {
        private Long billId;
        private String billNumber;
        private String customerName;
        private BigDecimal amount;
        private List<BillStockItemResponse> systemItems;
        /** Per-product comparison: system qty vs this child bill's qty */
        private List<ItemComparisonRow> comparison;
        private String linkedByName;
        private LocalDateTime linkedAt;
        private String notes;
    }

    @Data
    @Builder
    public static class ItemComparisonRow {
        private Long productId;
        private String productName;
        private Long systemQty;
        private Long childQty;
        /** childQty - systemQty; negative means short in child */
        private Long diff;
    }
}
