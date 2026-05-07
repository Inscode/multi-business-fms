package com.multi.finance.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BillRequest {

    @NotBlank(message = "Business is required")
    private String business;

    @NotBlank(message = "Division is required")
    private String division;

    @NotBlank(message = "Bill type is required")
    private String billType;

    @NotBlank(message = "Bill source is required")
    private String billSource;

    private String billNumber;

    @NotBlank(message = "Customer name is required")
    private String customerName;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal totalAmount;

    private Long workerId;

    private LocalDate billDate;

    private String notes;

}
