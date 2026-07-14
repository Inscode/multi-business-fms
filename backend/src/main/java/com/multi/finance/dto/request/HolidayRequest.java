package com.multi.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class HolidayRequest {
    @NotNull
    private LocalDate date;
    @NotBlank
    private String name;
    @NotNull
    private String holidayType;   // POYA, NATIONAL, PUBLIC, OTHER
    private boolean appliesToAll = true;
    private List<Long> workerIds; // populated when appliesToAll=false
}
