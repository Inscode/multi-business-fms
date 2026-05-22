package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PaymentSummaryResponse {
    private Long id;
    private String billNumber;
    private String customerName;
    private BigDecimal amount;
    private String paymentType;
    private String enteredByName;
    private LocalDate paymentDate;
    private String status;
    private String collectedByOwnerName;
}
