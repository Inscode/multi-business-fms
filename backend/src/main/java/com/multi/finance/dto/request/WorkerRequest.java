package com.multi.finance.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class WorkerRequest {
    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Worker type is required")
    private String workerType;


    @NotNull(message = "Base salary is required")
    private BigDecimal baseSalary;

    @NotNull(message = "Joined date is required")
    private LocalDate joinedDate;

    private String notes;

}
