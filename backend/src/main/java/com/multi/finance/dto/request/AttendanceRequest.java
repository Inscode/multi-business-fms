package com.multi.finance.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AttendanceRequest {
    @NotNull
    private Long workerId;
    @NotNull
    private LocalDate date;
    @NotNull
    private String attendanceType;   // PRESENT, HALF_DAY, ABSENT, HOLIDAY
    private BigDecimal hoursWorked;
    private String notes;
}
