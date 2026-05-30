package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SummaryLoadBillResponse {

    private Long id;
    private List<Long> systemBillIds;
    private Integer numberOfBills;
    private Long totalQuantity;
    private LocalDate loadDate;
    private String notes;
    private String status; // PENDING, APPROVED, REJECTED
    private String createdByName;
    private LocalDateTime createdAt;
    private String approvedByName;
    private LocalDateTime approvedAt;
}
