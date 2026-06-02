package com.multi.finance.dto.request;

import com.multi.finance.enums.AdvanceBonusType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WorkerAdvanceBonusRequest {
    private Long recipientId;
    private AdvanceBonusType type;
    private BigDecimal amount;
    private String reason;
    private String month;
    private String notes;
}
