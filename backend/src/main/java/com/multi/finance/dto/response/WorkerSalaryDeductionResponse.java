package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class WorkerSalaryDeductionResponse {
    private Long id;
    private Long recipientId;
    private String recipientName;
    private String deductionMonth;
    private BigDecimal amount;
    private String deductionType;
    private Long referenceId;
    private String description;
    private String createdByName;
    private LocalDateTime createdAt;
}
