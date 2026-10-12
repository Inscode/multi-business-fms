package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder
public class ExpenseAdvanceResponse {
    private Long id;
    private String business;
    private String targetType;
    private Long deliveryRunId;
    private String deliveryRoute;
    private Long collectorId;
    private String collectorName;
    private LocalDate activityDate;
    private BigDecimal amount;
    private BigDecimal returnedAmount;
    private String status;
    private String notes;
    private String issuedByName;
    private LocalDateTime createdAt;
}
