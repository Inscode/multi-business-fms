package com.multi.finance.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class TimeEntryRequest {
    @NotNull
    private Long workerId;
    @NotNull
    private LocalDate date;
    @NotNull
    private LocalTime clockIn;
    private LocalTime clockOut;
    private String notes;
}
