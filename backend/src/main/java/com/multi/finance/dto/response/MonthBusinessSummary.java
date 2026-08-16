package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** One business's month: what was billed, what came in, what is still out. */
@Data
@Builder
public class MonthBusinessSummary {
    private String business;
    private long billCount;
    private BigDecimal sales;
    private BigDecimal paid;
    private BigDecimal pending;
}
