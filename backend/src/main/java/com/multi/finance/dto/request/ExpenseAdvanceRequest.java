package com.multi.finance.dto.request;

import com.multi.finance.enums.BusinessType;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpenseAdvanceRequest {
    private BusinessType business;
    private String targetType;
    private Long deliveryRunId;
    private Long collectorId;
    private LocalDate activityDate;
    private BigDecimal amount;
    private String notes;
}
