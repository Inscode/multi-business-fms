package com.multi.finance.dto.response;

import com.multi.finance.enums.ReminderPeriod;
import com.multi.finance.enums.ReminderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class BillReminderResponse {
    private Long id;
    private Long billId;
    private String billNumber;
    private String customerName;
    private String area;
    private BigDecimal balanceRemaining;
    private LocalDate reminderDate;
    private ReminderPeriod period;
    private String note;
    private ReminderStatus status;
    private String createdByName;
    private LocalDateTime createdAt;
}