package com.multi.finance.dto.response;

import com.multi.finance.enums.AdvanceBonusType;
import com.multi.finance.enums.WorkerFinanceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class WorkerAdvanceBonusResponse {
    private Long id;
    private Long recipientId;
    private String recipientName;
    private AdvanceBonusType type;
    private BigDecimal amount;
    private String reason;
    private String month;
    private LocalDate paymentDate;
    private WorkerFinanceStatus status;
    private BigDecimal recoveredAmount;
    private Boolean fullyRecovered;
    private String notes;
    private String enteredByName;
    private String ownerReviewedByName;
    private LocalDateTime ownerReviewedAt;
    private String rejectionReason;
    private String adminConfirmedByName;
    private LocalDateTime adminConfirmedAt;
    private LocalDateTime createdAt;
}
