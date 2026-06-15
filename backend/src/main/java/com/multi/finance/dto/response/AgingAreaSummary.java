package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class AgingAreaSummary {
    private String area;
    private BigDecimal totalOutstanding;
    private BigDecimal overdue;      // 45+ days
    private BigDecimal current;      // 0–30 days
    private BigDecimal days31to60;
    private BigDecimal days61to90;
    private BigDecimal days91plus;
    private BigDecimal cashPending;
    private BigDecimal cashSerious;
    private int customerCount;
    private int billCount;
    private List<AgingCustomerEntry> customers;
}
