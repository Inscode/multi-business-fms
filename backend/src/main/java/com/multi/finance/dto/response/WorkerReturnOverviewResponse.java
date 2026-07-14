package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class WorkerReturnOverviewResponse {
    private Long id;
    private Long billId;
    private String billNumber;
    private String customerName;
    private String area;
    private String returnType;
    private String status;
    private BigDecimal itemsTotal;
    private BigDecimal calculatedReturnAmount;
    private String notes;
    private String responsibleWorkerName;
    private String submittedAt;
}
