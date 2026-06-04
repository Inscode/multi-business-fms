package com.multi.finance.dto.request;

import com.multi.finance.enums.BillSource;
import com.multi.finance.enums.BillType;
import com.multi.finance.enums.BusinessType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BillRequest {

    @NotNull(message = "Business is required")
    private BusinessType business;

    @NotBlank(message = "Division is required")
    private String division;

    @NotNull(message = "Bill type is required")
    private BillType billType;

    @NotNull(message = "Bill source is required")
    private BillSource billSource;

    private String area;

    private String billNumber;

    @NotBlank(message = "Customer name is required")
    private String customerName;

    private Long customerId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal totalAmount;

    private Long workerId;

    private LocalDate billDate;

    private String notes;

}
