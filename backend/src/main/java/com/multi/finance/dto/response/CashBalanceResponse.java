package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CashBalanceResponse {
    private BigDecimal totalFunded;
    private BigDecimal totalSpent;
    private BigDecimal balance;
}