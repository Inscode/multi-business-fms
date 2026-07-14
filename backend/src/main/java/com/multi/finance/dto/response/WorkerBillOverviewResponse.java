package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class WorkerBillOverviewResponse {
    private Long id;
    private String billNumber;
    private String customerName;
    private String area;
    private String status;
    private String business;
    private String billType;
    private String billDate;
    private BigDecimal totalAmount;
    private BigDecimal balanceRemaining;
    private String workerName;
    private Boolean collectionOnly;
    private String createdAt;
}
