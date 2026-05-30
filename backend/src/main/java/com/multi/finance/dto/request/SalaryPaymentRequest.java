package com.multi.finance.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SalaryPaymentRequest {
    private Long recipientId;
    private String month;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String notes;
}