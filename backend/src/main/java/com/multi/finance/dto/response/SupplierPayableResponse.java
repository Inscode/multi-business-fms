package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class SupplierPayableResponse {
    private Long id;
    private String business;
    private String supplierName;
    private String description;
    private BigDecimal amount;
    private LocalDate dueDate;
    private String chequeNumber;
    private String bankName;
    private Boolean settled;
    private LocalDate settledOn;
    private String notes;
    private String createdByName;
    private long daysUntilDue;
}
