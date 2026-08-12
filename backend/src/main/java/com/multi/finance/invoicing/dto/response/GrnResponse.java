package com.multi.finance.invoicing.dto.response;

import com.multi.finance.invoicing.enums.CategoryType;
import com.multi.finance.invoicing.enums.GrnStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class GrnResponse {
    private Long id;
    private String grnNo;
    private CategoryType category;
    private String supplierName;
    private LocalDate receivedDate;
    private Integer paymentTermsDays;
    private LocalDate dueDate;
    private Boolean paymentRequired;
    private GrnStatus status;
    private String rejectionReason;
    private String notes;
    private String submittedBy;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private BigDecimal discountPct;
    /** Sum of qty x unit cost, before discount — the document's "total gross value". */
    private BigDecimal totalCost;
    private BigDecimal discountAmount;
    /** What is actually payable: gross less the discount. */
    private BigDecimal netTotal;
    private Integer totalQty;
    private List<GrnLineResponse> lines;

    @Data
    public static class GrnLineResponse {
        private Long id;
        private Long itemId;
        private String itemCode;
        private String itemDescription;
        private String brandName;
        private Integer qty;
        private BigDecimal unitCost;
        /** qty x unit cost, before discount */
        private BigDecimal lineTotal;
        /** line total less this note's discount */
        private BigDecimal netTotal;
    }
}
