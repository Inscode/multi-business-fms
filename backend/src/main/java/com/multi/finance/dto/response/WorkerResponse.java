package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class WorkerResponse {
    private Long id;
    private String fullName;
    private String workerType;
    private BigDecimal baseSalary;
    private Boolean active;
    private LocalDate joinedDate;
    private String notes;
    private LocalDateTime createdAt;

}
