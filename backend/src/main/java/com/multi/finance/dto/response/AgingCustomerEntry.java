package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class AgingCustomerEntry {
    private String customerName;
    private Long customerId;
    private String area;
    private BigDecimal totalOutstanding;
    private BigDecimal overdue;          // 45+ days (business overdue threshold)
    private BigDecimal current;          // 0–30 days
    private BigDecimal days31to60;
    private BigDecimal days61to90;
    private BigDecimal days91plus;
    private int billCount;
    private LocalDate oldestBillDate;
    private LocalDate lastPaymentDate;   // null = never paid

    // Cash-bill aging (separate thresholds: 0=normal, 1-7=followup, 8-14=urgent, 15+=serious)
    private BigDecimal cashPending;      // total outstanding cash balance for this customer
    private BigDecimal cashFollowUp;     // 1–7 days
    private BigDecimal cashUrgent;       // 8–14 days
    private BigDecimal cashSerious;      // 15+ days
}
