package com.multi.finance.dto.response;

import com.multi.finance.enums.BusinessType;
import com.multi.finance.enums.ExpenseCategory;
import com.multi.finance.enums.ExpenseStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ExpenseResponse {
    private Long id;
    private BusinessType business;
    private ExpenseCategory category;
    private BigDecimal amount;
    private LocalDate date;
    private String description;
    private String workerName;
    private ExpenseStatus status;
    private String enteredByName;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}