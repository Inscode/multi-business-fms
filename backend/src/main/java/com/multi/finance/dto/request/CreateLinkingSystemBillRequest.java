package com.multi.finance.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateLinkingSystemBillRequest {

    @NotBlank(message = "Bill number is required")
    private String billNumber;

    @NotBlank(message = "Customer name is required")
    private String customerName;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "Bill date is required")
    private LocalDate billDate;

    private String notes;
}
