package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class CallListItemDTO {
    private String callReason;
    private String customerName;
    private String phone;
    private String area;
    private String tier;
    private String business;
    private String billNumber;
    private String billType;
    private LocalDate billDate;
    private BigDecimal balanceRemaining;
    private long daysSinceBill;
    private long daysOverdue;
    private LocalDate reminderDate;
    private String reminderNote;
}
