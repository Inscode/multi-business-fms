package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CollectorPerformanceEntry {
    private Long workerId;
    private String workerName;
    private BigDecimal totalCollected;
    private int paymentCount;
    private double avgDaysToCollect;
    private double partialRate;   // 0..1
}
