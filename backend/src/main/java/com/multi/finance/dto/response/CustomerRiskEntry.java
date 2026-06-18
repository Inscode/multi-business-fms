package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CustomerRiskEntry {
    private String customerName;
    private String area;
    private int partialCount;
    private int returnedCount;
    private BigDecimal currentOutstanding;
    private int riskScore;   // partialCount + returnedCount*2
}
