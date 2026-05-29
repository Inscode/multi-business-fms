package com.multi.finance.dto.request;

import com.multi.finance.enums.BusinessType;
import com.multi.finance.enums.ExpenseCategory;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpenseRequest {
    private BusinessType business;
    private ExpenseCategory category;
    private BigDecimal amount;
    private LocalDate date;
    private String description;
    private Long workerId;
}