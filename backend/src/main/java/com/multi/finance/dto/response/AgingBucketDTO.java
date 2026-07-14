package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AgingBucketDTO {
    private int count0to30;
    private BigDecimal amount0to30;
    private int count31to60;
    private BigDecimal amount31to60;
    private int count61to90;
    private BigDecimal amount61to90;
    private int count90plus;
    private BigDecimal amount90plus;
    private int totalBills;
    private BigDecimal totalOutstanding;
    private long oldestDays;
}
