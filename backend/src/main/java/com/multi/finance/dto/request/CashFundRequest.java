package com.multi.finance.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CashFundRequest {
    private BigDecimal amount;
    private LocalDate date;
    private String description;
}