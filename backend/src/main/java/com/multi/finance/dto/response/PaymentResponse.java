package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponse {
    private Long id;
    private Long billId;
    private String customerName;
    private String business;
    private BigDecimal billTotal;
    private BigDecimal amountPaid;
    private BigDecimal balanceRemaining;
    private Boolean fullyPaid;
    private BigDecimal paymentAmount;
    private String paymentType;
    private String status;
    private String enteredByName;
    private String confirmedByName;
    private LocalDateTime confirmedAt;
    private LocalDate paymentDate;
    private String notes;
    private LocalDateTime createdAt;
}
