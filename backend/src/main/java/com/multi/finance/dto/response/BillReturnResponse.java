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
}