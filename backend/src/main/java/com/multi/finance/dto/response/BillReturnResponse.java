package com.multi.finance.dto.response;

import com.multi.finance.enums.ReturnStatus;
import com.multi.finance.enums.ReturnType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BillReturnResponse {
    private Long id;
    private Long billId;
    private String billNumber;
    private String customerName;
    private String business;
    private ReturnType returnType;
    private ReturnStatus status;
    private List<BillReturnItemResponse> items;
    private BigDecimal itemsTotal;
    private BigDecimal discountPercentage;
    private BigDecimal discountFixed;
    private BigDecimal calculatedReturnAmount;
    private BigDecimal predictedValue;
    private String approvedWith;
    private BigDecimal approvedAmount;
    private String rejectionReason;
    private String notes;
    private String responsibleWorkerName;
    private String submittedByName;
    private LocalDateTime submittedAt;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private BigDecimal shortfallAmount;

    /** Items came off this bill's own invoice lines. */
    private Boolean fromSameBill;
    private Boolean cashSale;
    private BigDecimal cashDiscountPct;

    // Accountant's goods-received confirmation, read by the admin on review
    private String goodsReceipt;
    private String goodsConfirmedByName;
    private LocalDateTime goodsConfirmedAt;
    private String goodsConfirmedNote;

    /** A line credit was typed over — the admin should look at it. */
    private Boolean amountEdited;
    private String amountEditedBy;

    private Boolean stockApplied;

    // Admin reversal
    private String cancelledByName;
    private LocalDateTime cancelledAt;
    private String cancelReason;

    /** Still awaiting a confirmation or a review, and therefore blocking payment. */
    private Boolean open;
}