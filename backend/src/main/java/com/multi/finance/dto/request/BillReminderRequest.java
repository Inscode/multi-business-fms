package com.multi.finance.dto.request;

import com.multi.finance.enums.ReminderPeriod;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class BillReminderRequest {

    @NotNull(message = "Bill ID is required")
    private Long billId;

    @NotNull(message = "Reminder date is required")
    private LocalDate reminderDate;

    @NotNull(message = "Period is required")
    private ReminderPeriod period;

    private String note;
}