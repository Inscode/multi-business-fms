package com.multi.finance.dto.response;


import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class BillResponse {
    private Long id;
    private String billNumber;
    private String business;
    private String division;
    private String billType;
    private String billSource;
    private String customerName;
    private BigDecimal totalAmount;
    private String status;
    private Long workerId;
    private String workerName;
    private String enteredByName;
    private String receivedByName;
    private LocalDateTime receivedAt;
    private String confirmedByName;
    private LocalDateTime confirmedAt;
    private LocalDate billDate;
    private String notes;
    private LocalDateTime createdAt;
}
