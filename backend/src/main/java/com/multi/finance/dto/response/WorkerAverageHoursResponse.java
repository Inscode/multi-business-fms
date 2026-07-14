package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class WorkerAverageHoursResponse {
    private Long workerId;
    private String workerName;
    private String workerType;
    private int expectedWorkingDays;   // calendar days minus Sundays minus holidays
    private int presentDays;
    private int halfDays;
    private int absentDays;
    private int holidayDays;
    private BigDecimal totalHoursLogged;
    private BigDecimal averageHoursPerDay;  // totalHoursLogged / expectedWorkingDays
}
