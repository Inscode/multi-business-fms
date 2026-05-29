package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class CashFundResponse {
    private Long id;
    private BigDecimal amount;
    private LocalDate date;
    private String description;
    private String addedByName;
    private LocalDateTime createdAt;
}