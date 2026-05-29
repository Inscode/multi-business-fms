package com.multi.finance.dto.response;

import com.multi.finance.enums.RecipientDesignation;
import com.multi.finance.enums.SalaryPaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class SalaryPaymentResponse {
    private Long id;
    private Long recipientId;
    private String recipientName;
    private RecipientDesignation designation;
    private String month;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String notes;
    private SalaryPaymentStatus status;
    private Boolean overSalary;
    private BigDecimal monthlySalary;
    private BigDecimal alreadyPaid;
    private BigDecimal remainingAfter;
    private String enteredByName;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private String rejectionReason;
    private LocalDateTime createdAt;
}